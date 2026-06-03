package cs174a.ryanzanone.marketplace.db.model;

import java.math.BigDecimal;

/**
 * One row of {@code EMART_PRODUCT} (Project Description.txt §2.1).
 *
 * <p>Attributes and compatibility are intentionally not carried here — they
 * are fetched on demand via {@code ProductDao.attributesOf} /
 * {@code ProductDao.compatibleWith} so search result lists stay cheap.
 */
public record Product(
        String stockNumber,
        String category,
        String manufacturer,
        String modelNumber,
        int warrantyMonths,
        BigDecimal price) {
}
