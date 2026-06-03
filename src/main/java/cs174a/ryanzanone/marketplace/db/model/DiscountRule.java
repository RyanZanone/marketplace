package cs174a.ryanzanone.marketplace.db.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of {@code EMART_DISCOUNT_RULE} (Project Description.txt §2.3:
 * "the percentages and dollar amount in the discount rules … may change at
 * any time. It is thus necessary to store these rules in your database.").
 *
 * <p>{@code statusType} is one of {@code Gold}, {@code Silver}, {@code Green},
 * {@code New}. {@code discountPct} is in [0, 100].
 */
public record DiscountRule(
        String statusType,
        LocalDate effectiveDate,
        BigDecimal discountPct) {
}
