package cs174a.ryanzanone.marketplace.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import cs174a.ryanzanone.marketplace.db.model.InventoryItem;

/**
 * DAO for {@code EDEPOT_INVENTORY_ITEM} (Project Description.txt §3.1).
 *
 * <p>Stocks are mutated via {@link #deduct} (which fails atomically if the
 * remaining quantity would go negative) and {@link #add} (which does not
 * enforce the {@code max_stock_level} cap — the caller decides whether to
 * cap or warn, per the receive-shipment flow in {@code backup.tex} §4.5).
 *
 * <p>{@link #belowMin} and {@link #belowMax} drive the §3.2 replenishment
 * trigger ("if there are three or more items from the same manufacturer in
 * the inventory that go below their respective stock level, send a
 * replenishment order to the manufacturer").
 */
public final class InventoryDao {

    private static final String SELECT_COLS =
        "stock_number, manufacturer, model_number, quantity, min_stock_level, "
        + "max_stock_level, location, replenishment_qty";

    private InventoryDao() {}

    public static Optional<InventoryItem> findByStock(Connection conn, String stockNumber)
            throws SQLException {
        String sql = "SELECT " + SELECT_COLS + " FROM EDEPOT_INVENTORY_ITEM "
            + "WHERE stock_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stockNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readItem(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Lookup by candidate key {@code (manufacturer, model_number)}. Used by
     * {@code NoticeService.receiveNotice} to decide whether to assign a fresh
     * stock number or update an existing row.
     */
    public static Optional<InventoryItem> findByMfrModel(Connection conn,
                                                         String manufacturer,
                                                         String modelNumber) throws SQLException {
        String sql = "SELECT " + SELECT_COLS + " FROM EDEPOT_INVENTORY_ITEM "
            + "WHERE manufacturer = ? AND model_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, manufacturer);
            ps.setString(2, modelNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readItem(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Returns current on-hand quantity for each requested stock number;
     * missing keys are mapped to 0. Backs {@code InventoryService.checkAvailability}.
     */
    public static Map<String, Integer> availability(Connection conn,
                                                    Collection<String> stockNumbers)
            throws SQLException {
        if (stockNumbers == null || stockNumbers.isEmpty()) return Collections.emptyMap();
        String placeholders = String.join(",", Collections.nCopies(stockNumbers.size(), "?"));
        String sql = "SELECT stock_number, quantity FROM EDEPOT_INVENTORY_ITEM "
            + "WHERE stock_number IN (" + placeholders + ")";
        Map<String, Integer> found = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (String sn : stockNumbers) ps.setString(i++, sn);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    found.put(rs.getString("stock_number"), rs.getInt("quantity"));
                }
            }
        }
        // Preserve input order; default 0 for misses.
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String sn : stockNumbers) out.put(sn, found.getOrDefault(sn, 0));
        return out;
    }

    /**
     * Atomic decrement. {@code UPDATE … SET quantity = quantity - ? }
     * {@code WHERE stock_number = ? AND quantity >= ?}.
     *
     * @return rows affected. 0 means insufficient stock; the caller must
     *         not assume the write succeeded.
     */
    public static int deduct(Connection conn, String stockNumber, int qty) throws SQLException {
        String sql = "UPDATE EDEPOT_INVENTORY_ITEM SET quantity = quantity - ? "
            + "WHERE stock_number = ? AND quantity >= ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, qty);
            ps.setString(2, stockNumber);
            ps.setInt(3, qty);
            return ps.executeUpdate();
        }
    }

    /**
     * Increment {@code quantity}. Does NOT enforce {@code max_stock_level};
     * the caller (typically {@code ShipmentService.receiveShipment}) must
     * cap and surface a warning when an incoming shipment would overflow.
     */
    public static int add(Connection conn, String stockNumber, int qty) throws SQLException {
        String sql = "UPDATE EDEPOT_INVENTORY_ITEM SET quantity = quantity + ? "
            + "WHERE stock_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, qty);
            ps.setString(2, stockNumber);
            return ps.executeUpdate();
        }
    }

    /** Add (or subtract, with a negative delta) to {@code replenishment_qty}. */
    public static int adjustReplenishment(Connection conn, String stockNumber, int delta)
            throws SQLException {
        String sql = "UPDATE EDEPOT_INVENTORY_ITEM "
            + "SET replenishment_qty = replenishment_qty + ? WHERE stock_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setString(2, stockNumber);
            return ps.executeUpdate();
        }
    }

    /** Insert a fresh inventory row, typically for a brand-new product on a shipping notice. */
    public static void insert(Connection conn, InventoryItem item) throws SQLException {
        String sql = "INSERT INTO EDEPOT_INVENTORY_ITEM "
            + "(stock_number, manufacturer, model_number, quantity, min_stock_level, "
            + " max_stock_level, location, replenishment_qty) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.stockNumber());
            ps.setString(2, item.manufacturer());
            ps.setString(3, item.modelNumber());
            ps.setInt(4, item.quantity());
            ps.setInt(5, item.minStockLevel());
            ps.setInt(6, item.maxStockLevel());
            ps.setString(7, item.location());
            ps.setInt(8, item.replenishmentQty());
            ps.executeUpdate();
        }
    }

    /** Items for the given manufacturer whose {@code quantity < min_stock_level}. */
    public static List<InventoryItem> belowMin(Connection conn, String manufacturer)
            throws SQLException {
        return queryByManufacturer(conn, manufacturer,
            "AND quantity < min_stock_level");
    }

    /** Items for the given manufacturer whose {@code quantity < max_stock_level}. */
    public static List<InventoryItem> belowMax(Connection conn, String manufacturer)
            throws SQLException {
        return queryByManufacturer(conn, manufacturer,
            "AND quantity < max_stock_level");
    }

    private static List<InventoryItem> queryByManufacturer(Connection conn,
                                                           String manufacturer,
                                                           String extraPredicate)
            throws SQLException {
        String sql = "SELECT " + SELECT_COLS + " FROM EDEPOT_INVENTORY_ITEM "
            + "WHERE manufacturer = ? " + extraPredicate + " ORDER BY stock_number";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, manufacturer);
            try (ResultSet rs = ps.executeQuery()) {
                List<InventoryItem> out = new ArrayList<>();
                while (rs.next()) out.add(readItem(rs));
                return out;
            }
        }
    }

    private static InventoryItem readItem(ResultSet rs) throws SQLException {
        return new InventoryItem(
            rs.getString("stock_number"),
            rs.getString("manufacturer"),
            rs.getString("model_number"),
            rs.getInt("quantity"),
            rs.getInt("min_stock_level"),
            rs.getInt("max_stock_level"),
            rs.getString("location"),
            rs.getInt("replenishment_qty"));
    }
}
