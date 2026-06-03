package cs174a.ryanzanone.marketplace.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import cs174a.ryanzanone.marketplace.db.model.CartItem;

/**
 * DAO for {@code EMART_CART} and {@code EMART_CART_ITEM}
 * (Project Description.txt §2.3 shopping-cart operations).
 *
 * <p>The parent {@code EMART_CART} row is managed implicitly: every mutating
 * call ensures the parent exists (via {@code MERGE INTO EMART_CART}) and
 * bumps {@code last_updated}.
 */
public final class CartDao {

    private static final String ENSURE_CART =
        "MERGE INTO EMART_CART tgt "
            + "USING (SELECT ? AS cust FROM dual) src "
            + "ON (tgt.customer_id = src.cust) "
            + "WHEN MATCHED THEN UPDATE SET last_updated = SYSTIMESTAMP "
            + "WHEN NOT MATCHED THEN INSERT (customer_id, last_updated) "
            + "  VALUES (src.cust, SYSTIMESTAMP)";

    private CartDao() {}

    /** All cart line items for the given customer. Empty list if the cart is empty. */
    public static List<CartItem> findItems(Connection conn, String customerId) throws SQLException {
        String sql = "SELECT customer_id, stock_number, quantity FROM EMART_CART_ITEM "
            + "WHERE customer_id = ? ORDER BY stock_number";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                List<CartItem> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new CartItem(
                        rs.getString("customer_id"),
                        rs.getString("stock_number"),
                        rs.getInt("quantity")));
                }
                return out;
            }
        }
    }

    /**
     * Adds or replaces the line for {@code (customerId, stockNumber)} with
     * {@code qty}. Also ensures the parent {@code EMART_CART} row exists
     * and bumps its {@code last_updated} timestamp.
     */
    public static void upsertItem(Connection conn, String customerId, String stockNumber, int qty)
            throws SQLException {
        ensureCart(conn, customerId);
        String sql = "MERGE INTO EMART_CART_ITEM tgt "
            + "USING (SELECT ? AS cust, ? AS stk, ? AS qty FROM dual) src "
            + "ON (tgt.customer_id = src.cust AND tgt.stock_number = src.stk) "
            + "WHEN MATCHED THEN UPDATE SET quantity = src.qty "
            + "WHEN NOT MATCHED THEN INSERT (customer_id, stock_number, quantity) "
            + "  VALUES (src.cust, src.stk, src.qty)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customerId);
            ps.setString(2, stockNumber);
            ps.setInt(3, qty);
            ps.executeUpdate();
        }
    }

    /** Removes one line item. Returns rows affected (0 or 1). */
    public static int deleteItem(Connection conn, String customerId, String stockNumber)
            throws SQLException {
        String sql = "DELETE FROM EMART_CART_ITEM "
            + "WHERE customer_id = ? AND stock_number = ?";
        int n;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customerId);
            ps.setString(2, stockNumber);
            n = ps.executeUpdate();
        }
        if (n > 0) touch(conn, customerId);
        return n;
    }

    /** Empties the cart. Returns the number of line items removed. */
    public static int clear(Connection conn, String customerId) throws SQLException {
        String sql = "DELETE FROM EMART_CART_ITEM WHERE customer_id = ?";
        int n;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customerId);
            n = ps.executeUpdate();
        }
        if (n > 0) touch(conn, customerId);
        return n;
    }

    /** Updates {@code EMART_CART.last_updated} to {@code SYSTIMESTAMP}. No-op if no parent row. */
    public static void touch(Connection conn, String customerId) throws SQLException {
        String sql = "UPDATE EMART_CART SET last_updated = SYSTIMESTAMP WHERE customer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customerId);
            ps.executeUpdate();
        }
    }

    /** Internal: ensures the parent {@code EMART_CART} row exists. */
    private static void ensureCart(Connection conn, String customerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(ENSURE_CART)) {
            ps.setString(1, customerId);
            ps.executeUpdate();
        }
    }
}
