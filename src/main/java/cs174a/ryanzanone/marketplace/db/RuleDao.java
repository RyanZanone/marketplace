package cs174a.ryanzanone.marketplace.db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import cs174a.ryanzanone.marketplace.db.model.DiscountRule;
import cs174a.ryanzanone.marketplace.db.model.ShippingRule;

/**
 * DAO for {@code EMART_DISCOUNT_RULE} and {@code EMART_SHIPPING_RULE}
 * (Project Description.txt §2.3: "these rules may change at any time. It is
 * thus necessary to store these rules in your database").
 *
 * <p>Read methods always pick the row with the greatest
 * {@code effective_date <= SYSDATE} — that's the "currently effective"
 * policy. If no such row exists they throw {@link IllegalStateException}
 * (the seed in {@code schema.sql} guarantees one exists at bootstrap, so
 * missing rows mean a manager-driven misconfiguration).
 *
 * <p>Shipping rules: two well-known {@code rule_name} values, {@code pct}
 * (shipping fee as % of subtotal) and {@code waiver_threshold} (subtotal
 * above which shipping is waived). The "always free for new customers"
 * piece is enforced in checkout logic, not here.
 */
public final class RuleDao {

    private static final String SHIPPING_PCT_RULE = "pct";
    private static final String SHIPPING_WAIVER_RULE = "waiver_threshold";

    private RuleDao() {}

    /** Currently effective discount percentage for the given customer status. */
    public static BigDecimal currentDiscountPctFor(Connection conn, String status)
            throws SQLException {
        String sql = "SELECT discount_pct FROM EMART_DISCOUNT_RULE "
            + "WHERE status_type = ? AND effective_date <= SYSDATE "
            + "ORDER BY effective_date DESC FETCH FIRST 1 ROW ONLY";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal(1);
            }
        }
        throw new IllegalStateException(
            "No EMART_DISCOUNT_RULE row for status='" + status + "' effective on or before SYSDATE");
    }

    /** Currently effective shipping percentage. */
    public static BigDecimal currentShippingPct(Connection conn) throws SQLException {
        return currentShippingRuleValue(conn, SHIPPING_PCT_RULE);
    }

    /** Currently effective shipping-waiver threshold. */
    public static BigDecimal currentWaiverThreshold(Connection conn) throws SQLException {
        return currentShippingRuleValue(conn, SHIPPING_WAIVER_RULE);
    }

    private static BigDecimal currentShippingRuleValue(Connection conn, String ruleName)
            throws SQLException {
        String sql = "SELECT value FROM EMART_SHIPPING_RULE "
            + "WHERE rule_name = ? AND effective_date <= SYSDATE "
            + "ORDER BY effective_date DESC FETCH FIRST 1 ROW ONLY";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ruleName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal(1);
            }
        }
        throw new IllegalStateException(
            "No EMART_SHIPPING_RULE row for rule_name='" + ruleName + "' effective on or before SYSDATE");
    }

    /** Inserts a new discount rule (used by manager-driven policy change). */
    public static void insert(Connection conn, DiscountRule rule) throws SQLException {
        String sql = "INSERT INTO EMART_DISCOUNT_RULE (status_type, effective_date, discount_pct) "
            + "VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rule.statusType());
            ps.setDate(2, Date.valueOf(rule.effectiveDate()));
            ps.setBigDecimal(3, rule.discountPct());
            ps.executeUpdate();
        }
    }

    /** Inserts a new shipping rule. */
    public static void insert(Connection conn, ShippingRule rule) throws SQLException {
        String sql = "INSERT INTO EMART_SHIPPING_RULE (rule_name, effective_date, value) "
            + "VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rule.ruleName());
            ps.setDate(2, Date.valueOf(rule.effectiveDate()));
            ps.setBigDecimal(3, rule.value());
            ps.executeUpdate();
        }
    }
}
