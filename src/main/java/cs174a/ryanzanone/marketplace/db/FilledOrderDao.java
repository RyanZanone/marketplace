package cs174a.ryanzanone.marketplace.db;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import cs174a.ryanzanone.marketplace.db.model.FilledOrder;
import cs174a.ryanzanone.marketplace.db.model.FilledOrderItem;

/**
 * DAO for {@code EDEPOT_FILLED_ORDER} and {@code EDEPOT_FILLED_ORDER_ITEM} —
 * eDEPOT's side of the order-fulfillment record (Project Description.txt
 * §3.2 "Fill an order").
 *
 * <p>The PK is the eMART {@code order_number}; there is no separate sequence.
 * The {@code orderNumber} field on each input item is ignored on
 * {@link #insert} — the header's order number is used everywhere.
 */
public final class FilledOrderDao {

    private FilledOrderDao() {}

    /** Records that eDEPOT has filled the given eMART order. Items are batched. */
    public static void insert(Connection conn, FilledOrder header, List<FilledOrderItem> items)
            throws SQLException {
        String insertHeader = "INSERT INTO EDEPOT_FILLED_ORDER (order_number, filled_date) "
            + "VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertHeader)) {
            ps.setLong(1, header.orderNumber());
            ps.setDate(2, Date.valueOf(header.filledDate()));
            ps.executeUpdate();
        }
        if (items != null && !items.isEmpty()) {
            String insertItem = "INSERT INTO EDEPOT_FILLED_ORDER_ITEM "
                + "(order_number, stock_number, quantity) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertItem)) {
                for (FilledOrderItem item : items) {
                    ps.setLong(1, header.orderNumber());
                    ps.setString(2, item.stockNumber());
                    ps.setInt(3, item.quantity());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    public static Optional<FilledOrder> findById(Connection conn, long orderNumber)
            throws SQLException {
        String sql = "SELECT order_number, filled_date FROM EDEPOT_FILLED_ORDER "
            + "WHERE order_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new FilledOrder(
                        rs.getLong("order_number"),
                        rs.getDate("filled_date").toLocalDate()));
                }
                return Optional.empty();
            }
        }
    }

    public static List<FilledOrderItem> itemsOf(Connection conn, long orderNumber)
            throws SQLException {
        String sql = "SELECT order_number, stock_number, quantity FROM EDEPOT_FILLED_ORDER_ITEM "
            + "WHERE order_number = ? ORDER BY stock_number";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderNumber);
            try (ResultSet rs = ps.executeQuery()) {
                List<FilledOrderItem> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new FilledOrderItem(
                        rs.getLong("order_number"),
                        rs.getString("stock_number"),
                        rs.getInt("quantity")));
                }
                return out;
            }
        }
    }
}
