package cs174a.ryanzanone.marketplace.db.model;

/**
 * Search input for {@code ProductDao.search}. Any field may be {@code null};
 * non-null fields are AND'd together into the generated WHERE clause.
 *
 * <p>Backs the customer-search functionality from Project Description.txt §2.3:
 * "search for item(s) by stock number, manufacturer, model number, category,
 * description attribute and value, or … compatible items of an item; a search
 * condition can also be a combination of the above criteria."
 *
 * <p>Semantics of {@code compatibleWithStock}: returns products that can
 * replace the given stock number — i.e. rows of {@code EMART_COMPATIBILITY}
 * where {@code can_replace_stock = compatibleWithStock}, joined back to
 * {@code EMART_PRODUCT} on {@code product_stock}. This matches the customer
 * use-case from spec §2.3: "I'm looking at X — what could I use instead?"
 *
 * <p>Use {@link #empty()} for "match all rows".
 */
public record ProductSearchCriteria(
        String stockNumber,
        String manufacturer,
        String modelNumber,
        String category,
        String attrName,
        String attrValue,
        String compatibleWithStock) {

    public static ProductSearchCriteria empty() {
        return new ProductSearchCriteria(null, null, null, null, null, null, null);
    }

    /** True iff every field is null (i.e. the query degenerates to {@code SELECT *}). */
    public boolean isEmpty() {
        return stockNumber == null && manufacturer == null && modelNumber == null
                && category == null && attrName == null && attrValue == null
                && compatibleWithStock == null;
    }
}
