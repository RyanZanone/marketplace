package cs174a.ryanzanone.marketplace.db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import cs174a.ryanzanone.marketplace.db.model.Order;
import cs174a.ryanzanone.marketplace.db.model.OrderItem;

/**
 * DAO for {@code EMART_ORDERS} and {@code EMART_ORDER_ITEM}.
 *
 * <p>Backs checkout, order history display, re-run, and the manager's monthly
 * sales summary (Project Description.txt §2.3 + §2.4).
 *
 * <p>Order numbers come from {@code seq_emart_order} (see {@code schema.sql}).
 * On {@link #insert} the {@code number} field of the supplied {@link Order}
 * header is <em>ignored</em>; a fresh value is allocated and propagated to
 * each line item.
 */
public final class OrderDao {

    private static final String SELECT_COLS =
        "order_number, customer_id, order_date, subtotal, discount_pct_applied, shipping_fee, total";

    private OrderDao() {}

    /**
     * Inserts the order header (allocating a new {@code order_number} from
     * {@code seq_emart_order}) and batches the line items. Returns the new
     * order number. The {@code number} fields on the input header and items
     * are ignored — the freshly-allocated number is used everywhere.
     */
    public static long insert(Connection conn, Order header, List<OrderItem> items)
            throws SQLException {
        long orderNumber = nextOrderNumber(conn);

        String insertOrder = "INSERT INTO EMART_ORDERS "
            + "(order_number, customer_id, order_date, subtotal, "
            + " discount_pct_applied, shipping_fee, total) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertOrder)) {
            ps.setLong(1, orderNumber);
            ps.setString(2, header.customerId());
            ps.setDate(3, Date.valueOf(header.orderDate()));
            ps.setBigDecimal(4, header.subtotal());
            ps.setBigDecimal(5, header.discountPctApplied());
            ps.setBigDecimal(6, header.shippingFee());
            ps.setBigDecimal(7, header.total());
            ps.executeUpdate();
        }

        if (items != null && !items.isEmpty()) {
            String insertItem = "INSERT INTO EMART_ORDER_ITEM "
                + "(order_number, stock_number, quantity, unit_price_at_purchase) "
                + "VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertItem)) {
                for (OrderItem item : items) {
                    ps.setLong(1, orderNumber);
                    ps.setString(2, item.stockNumber());
                    ps.setInt(3, item.quantity());
                    ps.setBigDecimal(4, item.unitPriceAtPurchase());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        return orderNumber;
    }

    /** Returns the order header by number. */
    public static Optional<Order> findById(Connection conn, long orderNumber) throws SQLException {
        String sql = "SELECT " + SELECT_COLS + " FROM EMART_ORDERS WHERE order_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readOrder(rs)) : Optional.empty();
            }
        }
    }

    /** All line items for the given order. */
    public static List<OrderItem> itemsOf(Connection conn, long orderNumber) throws SQLException {
        String sql = "SELECT order_number, stock_number, quantity, unit_price_at_purchase "
            + "FROM EMART_ORDER_ITEM WHERE order_number = ? ORDER BY stock_number";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderNumber);
            try (ResultSet rs = ps.executeQuery()) {
                List<OrderItem> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new OrderItem(
                        rs.getLong("order_number"),
                        rs.getString("stock_number"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("unit_price_at_purchase")));
                }
                return out;
            }
        }
    }

    /**
     * The customer's most recent {@code n} orders, newest first. Used both
     * by the order-history UI and by {@link CustomerDao#recomputeStatus}.
     */
    public static List<Order> recentOrdersFor(Connection conn, String customerId, int n)
            throws SQLException {
        String sql = "SELECT " + SELECT_COLS + " FROM EMART_ORDERS WHERE customer_id = ? "
            + "ORDER BY order_date DESC, order_number DESC FETCH FIRST ? ROWS ONLY";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customerId);
            ps.setInt(2, n);
            try (ResultSet rs = ps.executeQuery()) {
                List<Order> out = new ArrayList<>();
                while (rs.next()) out.add(readOrder(rs));
                return out;
            }
        }
    }

    /**
     * Deletes the order iff it is NOT among the customer's most recent
     * {@code recentLimit} orders ({@code backup.tex} §4.5: "the manager
     * interface must filter so that only orders older than the most recent
     * three per customer can be deleted").
     *
     * @return rows affected (0 means it was protected or didn't exist)
     */
    public static int deleteIfNotInRecent(Connection conn, long orderNumber, int recentLimit)
            throws SQLException {
        String sql = "DELETE FROM EMART_ORDERS WHERE order_number = ? "
            + "AND order_number NOT IN ("
            + "  SELECT order_number FROM ("
            + "    SELECT order_number FROM EMART_ORDERS "
            + "    WHERE customer_id = ("
            + "      SELECT customer_id FROM EMART_ORDERS WHERE order_number = ?)"
            + "    ORDER BY order_date DESC, order_number DESC "
            + "    FETCH FIRST ? ROWS ONLY"
            + "  )"
            + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderNumber);
            ps.setLong(2, orderNumber);
            ps.setInt(3, recentLimit);
            return ps.executeUpdate();
        }
    }

    // -- Manager monthly summary helpers (Project Description.txt §2.4) ----

    /** Sum of {@code unit_price_at_purchase * quantity} grouped by stock_number, in {@code month}. */
    public static Map<String, BigDecimal> totalsByProduct(Connection conn, YearMonth month)
            throws SQLException {
        LocalDate from = month.atDay(1);
        LocalDate to = month.plusMonths(1).atDay(1);
        String sql = "SELECT oi.stock_number, SUM(oi.unit_price_at_purchase * oi.quantity) AS total "
            + "FROM EMART_ORDER_ITEM oi JOIN EMART_ORDERS o ON o.order_number = oi.order_number "
            + "WHERE o.order_date >= ? AND o.order_date < ? "
            + "GROUP BY oi.stock_number ORDER BY oi.stock_number";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, BigDecimal> out = new LinkedHashMap<>();
                while (rs.next()) {
                    out.put(rs.getString("stock_number"), rs.getBigDecimal("total"));
                }
                return out;
            }
        }
    }

    /** Same grouping but by {@code EMART_PRODUCT.category}. */
    public static Map<String, BigDecimal> totalsByCategory(Connection conn, YearMonth month)
            throws SQLException {
        LocalDate from = month.atDay(1);
        LocalDate to = month.plusMonths(1).atDay(1);
        String sql = "SELECT p.category, SUM(oi.unit_price_at_purchase * oi.quantity) AS total "
            + "FROM EMART_ORDER_ITEM oi "
            + "JOIN EMART_ORDERS o ON o.order_number = oi.order_number "
            + "JOIN EMART_PRODUCT p ON p.stock_number = oi.stock_number "
            + "WHERE o.order_date >= ? AND o.order_date < ? "
            + "GROUP BY p.category ORDER BY p.category";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, BigDecimal> out = new LinkedHashMap<>();
                while (rs.next()) {
                    out.put(rs.getString("category"), rs.getBigDecimal("total"));
                }
                return out;
            }
        }
    }

    /** Customer id with the highest spend in {@code month}, if any orders exist. */
    public static Optional<String> topCustomer(Connection conn, YearMonth month) throws SQLException {
        LocalDate from = month.atDay(1);
        LocalDate to = month.plusMonths(1).atDay(1);
        String sql = "SELECT customer_id FROM EMART_ORDERS "
            + "WHERE order_date >= ? AND order_date < ? "
            + "GROUP BY customer_id "
            + "ORDER BY SUM(total) DESC FETCH FIRST 1 ROW ONLY";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    private static long nextOrderNumber(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT seq_emart_order.NEXTVAL FROM dual")) {
            if (!rs.next()) throw new SQLException("seq_emart_order.NEXTVAL returned no row");
            return rs.getLong(1);
        }
    }

    private static Order readOrder(ResultSet rs) throws SQLException {
        return new Order(
            rs.getLong("order_number"),
            rs.getString("customer_id"),
            rs.getDate("order_date").toLocalDate(),
            rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("discount_pct_applied"),
            rs.getBigDecimal("shipping_fee"),
            rs.getBigDecimal("total"));
    }
}
