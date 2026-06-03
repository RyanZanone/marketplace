package cs174a.ryanzanone.marketplace.db.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of {@code EMART_ORDERS} (Project Description.txt §2.3 checkout).
 *
 * <p>Invariant enforced at insert time by the checkout transaction:
 * {@code total = subtotal * (1 - discountPctApplied/100) + shippingFee}
 * (see {@code backup.tex} §4.4).
 */
public record Order(
        long number,
        String customerId,
        LocalDate orderDate,
        BigDecimal subtotal,
        BigDecimal discountPctApplied,
        BigDecimal shippingFee,
        BigDecimal total) {
}
