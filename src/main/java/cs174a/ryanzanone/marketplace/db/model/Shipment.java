package cs174a.ryanzanone.marketplace.db.model;

import java.time.LocalDate;

/**
 * One row of {@code EDEPOT_SHIPMENT} — a physical delivery tied back to a
 * shipping notice (Project Description.txt §3.2: "Receive a shipment, always
 * having a prior shipping notice").
 */
public record Shipment(
        long shipmentId,
        long noticeId,
        LocalDate receivedDate) {
}
