package cs174a.ryanzanone.marketplace.db.model;

/**
 * One row of {@code EDEPOT_FILLED_ORDER_ITEM} — per-product line in a
 * filled order.
 */
public record FilledOrderItem(
        long orderNumber,
        String stockNumber,
        int quantity) {
}
