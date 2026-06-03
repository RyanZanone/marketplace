package cs174a.ryanzanone.marketplace.db.model;

import java.time.LocalDate;

/**
 * One row of {@code EDEPOT_FILLED_ORDER} — eDEPOT's record of having filled
 * an order originating from eMART (Project Description.txt §3.2 "Fill an
 * order"). PK is the eMART {@code order_number}, no sequence.
 */
public record FilledOrder(
        long orderNumber,
        LocalDate filledDate) {
}
