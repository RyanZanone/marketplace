package cs174a.ryanzanone.marketplace.db.model;

import java.time.LocalDate;

/**
 * One row of {@code EDEPOT_SHIPPING_NOTICE} (Project Description.txt §3.2:
 * "Receive a shipping notice from a manufacturer").
 *
 * <p>{@code status} is one of {@code pending}, {@code fulfilled}; flips to
 * {@code fulfilled} when the physical shipment arrives via
 * {@code NoticeDao.markFulfilled}.
 */
public record ShippingNotice(
        long noticeId,
        String shippingCompany,
        LocalDate receivedDate,
        String status) {
}
