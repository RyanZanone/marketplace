package cs174a.ryanzanone.marketplace.db.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of {@code EMART_SHIPPING_RULE}. Two rule names are seeded by
 * {@code schema.sql}: {@code pct} (shipping fee as a percentage of subtotal)
 * and {@code waiver_threshold} (subtotal above which shipping is waived).
 * Spec rules from Project Description.txt §2.3.
 */
public record ShippingRule(
        String ruleName,
        LocalDate effectiveDate,
        BigDecimal value) {
}
