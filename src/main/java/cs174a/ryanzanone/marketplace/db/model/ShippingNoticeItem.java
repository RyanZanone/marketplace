package cs174a.ryanzanone.marketplace.db.model;

/**
 * One row of {@code EDEPOT_SHIPPING_NOTICE_ITEM}.
 *
 * <p>{@code stockNumber} is nullable in the schema: a brand-new product
 * arrives on a notice with no assigned stock number, and
 * {@code NoticeService.receiveNotice} mints one via
 * {@code StockNumberGenerator} before populating it.
 */
public record ShippingNoticeItem(
        long noticeId,
        String manufacturer,
        String modelNumber,
        int quantity,
        String stockNumber) {
}
