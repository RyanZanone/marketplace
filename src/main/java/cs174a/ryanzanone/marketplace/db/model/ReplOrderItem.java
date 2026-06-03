package cs174a.ryanzanone.marketplace.db.model;

/**
 * One row of {@code EDEPOT_REPL_ORDER_ITEM}.
 */
public record ReplOrderItem(
        long replOrderId,
        String stockNumber,
        int quantity) {
}
