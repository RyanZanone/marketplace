package cs174a.ryanzanone.marketplace.db.model;

import java.time.LocalDate;

/**
 * One row of {@code EDEPOT_REPL_ORDER} — a replenishment order eDEPOT sent
 * to a manufacturer (Project Description.txt §3.2 "Fill an order" trigger,
 * and §2.4 manager's "Send an order to a manufacturer").
 *
 * <p>All items on a single {@code ReplOrder} must share the same
 * {@code manufacturer} (semantic constraint, {@code backup.tex} §4.4).
 */
public record ReplOrder(
        long replOrderId,
        String manufacturer,
        LocalDate sentDate) {
}
