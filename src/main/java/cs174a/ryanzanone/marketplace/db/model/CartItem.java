package cs174a.ryanzanone.marketplace.db.model;

/**
 * One row of {@code EMART_CART_ITEM}. The parent {@code EMART_CART} row is
 * managed implicitly by {@code CartDao} — callers don't see it.
 */
public record CartItem(
        String customerId,
        String stockNumber,
        int quantity) {
}
