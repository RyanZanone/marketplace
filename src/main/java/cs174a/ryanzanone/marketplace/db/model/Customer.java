package cs174a.ryanzanone.marketplace.db.model;

/**
 * One row of {@code EMART_CUSTOMER} (Project Description.txt §2.2). The
 * password hash is deliberately omitted from this record — authentication
 * lives entirely on {@code CustomerDao}.
 *
 * <p>{@code status} is one of {@code Gold}, {@code Silver}, {@code Green},
 * {@code New} (enforced by {@code ck_emart_customer_status}). Middle name
 * may be null.
 */
public record Customer(
        String id,
        String firstName,
        String middleName,
        String lastName,
        String email,
        String address,
        String status) {
}
