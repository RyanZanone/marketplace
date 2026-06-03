package cs174a.ryanzanone.marketplace.db.model;

import java.math.BigDecimal;

/**
 * One row of {@code EMART_ORDER_ITEM}. {@code unitPriceAtPurchase} captures
 * the price at the moment of checkout so subsequent price changes on the
 * product don't rewrite history.
 */
public record OrderItem(
        long orderNumber,
        String stockNumber,
        int quantity,
        BigDecimal unitPriceAtPurchase) {
}
