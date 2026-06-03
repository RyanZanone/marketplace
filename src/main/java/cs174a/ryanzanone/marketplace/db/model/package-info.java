/**
 * Immutable record models for each table family in the marketplace database.
 *
 * <p>One record per table (plus {@link cs174a.ryanzanone.marketplace.db.model.ProductSearchCriteria}
 * which is the assembled query input for {@code ProductDao.search}). DAOs in
 * {@link cs174a.ryanzanone.marketplace.db} return these records; the eMART
 * and eDEPOT service packages consume them.
 *
 * <p>Type mapping conventions:
 * <ul>
 *   <li>Money — {@link java.math.BigDecimal}</li>
 *   <li>Counts — {@code int}</li>
 *   <li>{@code DATE} — {@link java.time.LocalDate}</li>
 *   <li>{@code TIMESTAMP} — {@link java.time.Instant}</li>
 * </ul>
 *
 * <p>{@link cs174a.ryanzanone.marketplace.db.model.Customer} deliberately does
 * not carry {@code password_hash}; authentication lives entirely on the DAO.
 */
package cs174a.ryanzanone.marketplace.db.model;
