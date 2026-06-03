package cs174a.ryanzanone.marketplace.db.model;

/**
 * One row of {@code EMART_COMPATIBILITY}: {@code productStock} can replace
 * {@code canReplaceStock} (Project Description.txt §2.1, "Compatibility").
 */
public record Compatibility(
        String productStock,
        String canReplaceStock) {
}
