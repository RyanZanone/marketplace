package cs174a.ryanzanone.marketplace.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import cs174a.ryanzanone.marketplace.db.model.Shipment;

/**
 * DAO for {@code EDEPOT_SHIPMENT} (Project Description.txt §3.2: "Receive a
 * shipment, always having a prior shipping notice").
 *
 * <p>Shipment IDs come from {@code seq_edepot_shipment}.
 */
public final class ShipmentDao {

    private ShipmentDao() {}

    /**
     * Logs receipt of a physical shipment tied to the given notice. Returns
     * the new shipment id.
     */
    public static long insert(Connection conn, long noticeId) throws SQLException {
        long shipmentId = nextShipmentId(conn);
        String sql = "INSERT INTO EDEPOT_SHIPMENT (shipment_id, notice_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, shipmentId);
            ps.setLong(2, noticeId);
            ps.executeUpdate();
        }
        return shipmentId;
    }

    /** All shipments recorded against the given notice. */
    public static List<Shipment> findByNotice(Connection conn, long noticeId) throws SQLException {
        String sql = "SELECT shipment_id, notice_id, received_date FROM EDEPOT_SHIPMENT "
            + "WHERE notice_id = ? ORDER BY received_date DESC, shipment_id DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, noticeId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Shipment> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new Shipment(
                        rs.getLong("shipment_id"),
                        rs.getLong("notice_id"),
                        rs.getDate("received_date").toLocalDate()));
                }
                return out;
            }
        }
    }

    private static long nextShipmentId(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT seq_edepot_shipment.NEXTVAL FROM dual")) {
            if (!rs.next()) throw new SQLException("seq_edepot_shipment.NEXTVAL returned no row");
            return rs.getLong(1);
        }
    }
}
