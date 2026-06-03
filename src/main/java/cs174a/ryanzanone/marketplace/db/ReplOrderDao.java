package cs174a.ryanzanone.marketplace.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import cs174a.ryanzanone.marketplace.db.model.ReplOrder;
import cs174a.ryanzanone.marketplace.db.model.ReplOrderItem;

/**
 * DAO for {@code EDEPOT_REPL_ORDER} and {@code EDEPOT_REPL_ORDER_ITEM}.
 *
 * <p>Replenishment orders are written by two paths:
 * <ol>
 *   <li>The auto-trigger inside {@code InventoryService.fillOrder} when ≥3
 *       items of a manufacturer drop below their {@code min_stock_level}
 *       (Project Description.txt §3.2).</li>
 *   <li>The manager's "send order to a manufacturer" flow from §2.4 — the
 *       printed/displayed PO is logged here.</li>
 * </ol>
 *
 * <p>All items on a single replenishment order share the same manufacturer
 * (semantic constraint, {@code backup.tex} §4.4) — enforced by the caller.
 *
 * <p>Order IDs come from {@code seq_edepot_repl_order}. The {@code replOrderId}
 * fields on input items are ignored on {@link #insert}.
 */
public final class ReplOrderDao {

    private ReplOrderDao() {}

    /**
     * Inserts a replenishment order. Allocates a fresh id from the sequence
     * and batches the items. Caller must ensure all items map to the given
     * manufacturer (the DAO does not re-validate this).
     */
    public static long insert(Connection conn, String manufacturer, List<ReplOrderItem> items)
            throws SQLException {
        long replOrderId = nextReplOrderId(conn);

        String insertHeader = "INSERT INTO EDEPOT_REPL_ORDER (repl_order_id, manufacturer) "
            + "VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertHeader)) {
            ps.setLong(1, replOrderId);
            ps.setString(2, manufacturer);
            ps.executeUpdate();
        }

        if (items != null && !items.isEmpty()) {
            String insertItem = "INSERT INTO EDEPOT_REPL_ORDER_ITEM "
                + "(repl_order_id, stock_number, quantity) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertItem)) {
                for (ReplOrderItem item : items) {
                    ps.setLong(1, replOrderId);
                    ps.setString(2, item.stockNumber());
                    ps.setInt(3, item.quantity());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        return replOrderId;
    }

    public static Optional<ReplOrder> findById(Connection conn, long replOrderId)
            throws SQLException {
        String sql = "SELECT repl_order_id, manufacturer, sent_date FROM EDEPOT_REPL_ORDER "
            + "WHERE repl_order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, replOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readReplOrder(rs)) : Optional.empty();
            }
        }
    }

    public static List<ReplOrderItem> itemsOf(Connection conn, long replOrderId)
            throws SQLException {
        String sql = "SELECT repl_order_id, stock_number, quantity FROM EDEPOT_REPL_ORDER_ITEM "
            + "WHERE repl_order_id = ? ORDER BY stock_number";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, replOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ReplOrderItem> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new ReplOrderItem(
                        rs.getLong("repl_order_id"),
                        rs.getString("stock_number"),
                        rs.getInt("quantity")));
                }
                return out;
            }
        }
    }

    /** Newest {@code n} replenishment orders, for the manager interface. */
    public static List<ReplOrder> findRecent(Connection conn, int n) throws SQLException {
        String sql = "SELECT repl_order_id, manufacturer, sent_date FROM EDEPOT_REPL_ORDER "
            + "ORDER BY sent_date DESC, repl_order_id DESC FETCH FIRST ? ROWS ONLY";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, n);
            try (ResultSet rs = ps.executeQuery()) {
                List<ReplOrder> out = new ArrayList<>();
                while (rs.next()) out.add(readReplOrder(rs));
                return out;
            }
        }
    }

    private static long nextReplOrderId(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT seq_edepot_repl_order.NEXTVAL FROM dual")) {
            if (!rs.next()) throw new SQLException("seq_edepot_repl_order.NEXTVAL returned no row");
            return rs.getLong(1);
        }
    }

    private static ReplOrder readReplOrder(ResultSet rs) throws SQLException {
        return new ReplOrder(
            rs.getLong("repl_order_id"),
            rs.getString("manufacturer"),
            rs.getDate("sent_date").toLocalDate());
    }
}
