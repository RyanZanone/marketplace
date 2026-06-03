package cs174a.ryanzanone.marketplace.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import cs174a.ryanzanone.marketplace.db.model.ShippingNotice;
import cs174a.ryanzanone.marketplace.db.model.ShippingNoticeItem;

/**
 * DAO for {@code EDEPOT_SHIPPING_NOTICE} and
 * {@code EDEPOT_SHIPPING_NOTICE_ITEM} (Project Description.txt §3.2: "Receive
 * a shipping notice from a manufacturer").
 *
 * <p>Notice IDs come from {@code seq_edepot_notice}. The {@code noticeId}
 * fields on the input items are ignored on {@link #insert} — the freshly
 * allocated id is used for every line.
 *
 * <p>Note: an item's {@code stock_number} may be null until
 * {@code NoticeService.receiveNotice} mints one via {@link StockNumberGenerator}
 * and patches it.
 */
public final class NoticeDao {

    private NoticeDao() {}

    /**
     * Inserts a new shipping notice with the given items. Allocates a fresh
     * notice id from {@code seq_edepot_notice} and returns it. Status starts
     * as {@code pending} (the DB default).
     */
    public static long insert(Connection conn, String shippingCompany,
                              List<ShippingNoticeItem> items) throws SQLException {
        long noticeId = nextNoticeId(conn);

        String insertHeader = "INSERT INTO EDEPOT_SHIPPING_NOTICE "
            + "(notice_id, shipping_company) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertHeader)) {
            ps.setLong(1, noticeId);
            ps.setString(2, shippingCompany);
            ps.executeUpdate();
        }

        if (items != null && !items.isEmpty()) {
            String insertItem = "INSERT INTO EDEPOT_SHIPPING_NOTICE_ITEM "
                + "(notice_id, manufacturer, model_number, quantity, stock_number) "
                + "VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertItem)) {
                for (ShippingNoticeItem item : items) {
                    ps.setLong(1, noticeId);
                    ps.setString(2, item.manufacturer());
                    ps.setString(3, item.modelNumber());
                    ps.setInt(4, item.quantity());
                    if (item.stockNumber() == null) ps.setNull(5, Types.VARCHAR);
                    else ps.setString(5, item.stockNumber());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        return noticeId;
    }

    public static Optional<ShippingNotice> findById(Connection conn, long noticeId)
            throws SQLException {
        String sql = "SELECT notice_id, shipping_company, received_date, status "
            + "FROM EDEPOT_SHIPPING_NOTICE WHERE notice_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, noticeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readNotice(rs)) : Optional.empty();
            }
        }
    }

    public static List<ShippingNoticeItem> itemsOf(Connection conn, long noticeId)
            throws SQLException {
        String sql = "SELECT notice_id, manufacturer, model_number, quantity, stock_number "
            + "FROM EDEPOT_SHIPPING_NOTICE_ITEM WHERE notice_id = ? "
            + "ORDER BY manufacturer, model_number";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, noticeId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ShippingNoticeItem> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new ShippingNoticeItem(
                        rs.getLong("notice_id"),
                        rs.getString("manufacturer"),
                        rs.getString("model_number"),
                        rs.getInt("quantity"),
                        rs.getString("stock_number")));
                }
                return out;
            }
        }
    }

    /** Flip {@code status} from {@code pending} to {@code fulfilled}. */
    public static int markFulfilled(Connection conn, long noticeId) throws SQLException {
        String sql = "UPDATE EDEPOT_SHIPPING_NOTICE SET status = 'fulfilled' "
            + "WHERE notice_id = ? AND status = 'pending'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, noticeId);
            return ps.executeUpdate();
        }
    }

    /** All notices still in the {@code pending} state, newest first. */
    public static List<ShippingNotice> findPending(Connection conn) throws SQLException {
        String sql = "SELECT notice_id, shipping_company, received_date, status "
            + "FROM EDEPOT_SHIPPING_NOTICE WHERE status = 'pending' "
            + "ORDER BY received_date DESC, notice_id DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<ShippingNotice> out = new ArrayList<>();
            while (rs.next()) out.add(readNotice(rs));
            return out;
        }
    }

    private static long nextNoticeId(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT seq_edepot_notice.NEXTVAL FROM dual")) {
            if (!rs.next()) throw new SQLException("seq_edepot_notice.NEXTVAL returned no row");
            return rs.getLong(1);
        }
    }

    private static ShippingNotice readNotice(ResultSet rs) throws SQLException {
        return new ShippingNotice(
            rs.getLong("notice_id"),
            rs.getString("shipping_company"),
            rs.getDate("received_date").toLocalDate(),
            rs.getString("status"));
    }
}
