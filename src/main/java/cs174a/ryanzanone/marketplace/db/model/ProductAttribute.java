package cs174a.ryanzanone.marketplace.db.model;

/**
 * One row of {@code EMART_PRODUCT_ATTRIBUTE} — a single (name, value) pair
 * from a product's description (Project Description.txt §2.1, "Description").
 */
public record ProductAttribute(
        String stockNumber,
        String name,
        String value) {
}
