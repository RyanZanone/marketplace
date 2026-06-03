package cs174a.ryanzanone.marketplace.db.model;

/**
 * One row of {@code EDEPOT_INVENTORY_ITEM} (Project Description.txt §3.1).
 *
 * <p>Schema invariants: {@code 0 <= quantity <= maxStockLevel},
 * {@code minStockLevel <= maxStockLevel}, {@code replenishmentQty >= 0}.
 * Location format is {@code <letter><number>} with no leading zero.
 */
public record InventoryItem(
        String stockNumber,
        String manufacturer,
        String modelNumber,
        int quantity,
        int minStockLevel,
        int maxStockLevel,
        String location,
        int replenishmentQty) {
}
