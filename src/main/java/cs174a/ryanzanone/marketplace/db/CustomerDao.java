package cs174a.ryanzanone.marketplace.db;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import cs174a.ryanzanone.marketplace.db.model.Customer;
import cs174a.ryanzanone.marketplace.db.model.Order;

/**
 * DAO for {@code EMART_CUSTOMER} (Project Description.txt §2.2).
 *
 * <p>Authentication: SHA-256 hex digest of the password is compared against
 * {@code password_hash} (which is {@code VARCHAR2(64)} — exactly the length
 * of a SHA-256 hex string).
 *
 * <p>{@link #recomputeStatus} implements the Gold/Silver/Green/New brackets
 * from §2.2 against the customer's three most recent orders. Per the spec
 * the bracket thresholds (500 and 100) are not policy-tunable like the
 * discount percentages are, so they are constants here.
 */
public final class CustomerDao {

    private static final String SELECT_COLS =
        "customer_id, first_name, middle_name, last_name, email, address, status";

    public static final int STATUS_WINDOW = 3;
    
    private static final BigDecimal GOLD_THRESHOLD = new BigDecimal("500");
    private static final BigDecimal SILVER_THRESHOLD = new BigDecimal("100");

    public static final String STATUS_GOLD = "Gold";
    public static final String STATUS_SILVER = "Silver";
    public static final String STATUS_GREEN = "Green";
    public static final String STATUS_NEW = "New";

    private CustomerDao() {}

    /** Returns the customer (without password hash) by id. */
    public static Optional<Customer> findById(Connection conn, String id) throws SQLException {
        String sql = "SELECT " + SELECT_COLS + " FROM EMART_CUSTOMER WHERE customer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readCustomer(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Returns the customer iff the supplied password's SHA-256 hex digest
     * equals their stored {@code password_hash}.
     */
    public static Optional<Customer> authenticate(Connection conn, String id, String password)
            throws SQLException {
        String sql = "SELECT " + SELECT_COLS + " FROM EMART_CUSTOMER "
            + "WHERE customer_id = ? AND password_hash = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, sha256Hex(password));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readCustomer(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Inserts a new customer with the supplied plaintext password (hashed
     * before persisting). The {@code status} field of the supplied body is
     * ignored — new customers always start at {@code New} per §2.2.
     */
    public static void insert(Connection conn, String id, String password, Customer body)
            throws SQLException {
        String sql = "INSERT INTO EMART_CUSTOMER "
            + "(customer_id, password_hash, first_name, middle_name, last_name, "
            + " email, address, status) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, sha256Hex(password));
            ps.setString(3, body.firstName());
            ps.setString(4, body.middleName());
            ps.setString(5, body.lastName());
            ps.setString(6, body.email());
            ps.setString(7, body.address());
            ps.setString(8, STATUS_NEW);
            ps.executeUpdate();
        }
    }

    /**
     * Manager flow from Project Description.txt §2.4 ("customer status
     * adjustment … by manager's decision"). Caller must pass one of
     * {@link #STATUS_GOLD}, {@link #STATUS_SILVER}, {@link #STATUS_GREEN},
     * {@link #STATUS_NEW}; the DB check constraint will reject anything else.
     */
    public static int updateStatus(Connection conn, String customerId, String status)
            throws SQLException {
        String sql = "UPDATE EMART_CUSTOMER SET status = ? WHERE customer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, customerId);
            return ps.executeUpdate();
        }
    }

    /**
     * Recomputes status from the customer's most recent {@link #STATUS_WINDOW}
     * orders and writes it back. Returns the new status. Spec brackets (§2.2):
     * Gold {@code > 500}, Silver {@code > 100 and <= 500}, Green {@code > 0 and <= 100},
     * New if no orders exist at all.
     */
    public static String recomputeStatus(Connection conn, String customerId) throws SQLException {
        List<Order> recent = OrderDao.recentOrdersFor(conn, customerId, STATUS_WINDOW);
        String status;
        if (recent.isEmpty()) {
            status = STATUS_NEW;
        } else {
            BigDecimal sum = BigDecimal.ZERO;
            for (Order o : recent) sum = sum.add(o.total());
            status = bracketFor(sum);
        }
        updateStatus(conn, customerId, status);
        return status;
    }

    /**
     * SHA-256 hex digest, used by {@link #authenticate} and {@link #insert}.
     * Public so tests can verify the column's expected value without
     * involving JDBC.
     */
    public static String sha256Hex(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Visible for tests. Maps a non-zero spend to a status bracket. */
    static String bracketFor(BigDecimal totalSpend) {
        if (totalSpend.compareTo(GOLD_THRESHOLD) > 0) return STATUS_GOLD;
        if (totalSpend.compareTo(SILVER_THRESHOLD) > 0) return STATUS_SILVER;
        if (totalSpend.compareTo(BigDecimal.ZERO) > 0) return STATUS_GREEN;
        return STATUS_NEW;
    }

    private static Customer readCustomer(ResultSet rs) throws SQLException {
        return new Customer(
            rs.getString("customer_id"),
            rs.getString("first_name"),
            rs.getString("middle_name"),
            rs.getString("last_name"),
            rs.getString("email"),
            rs.getString("address"),
            rs.getString("status"));
    }
}
