package org.linlinjava.litemall.order.application.util.exception.groupbuy;

/**
 * Typed group-buy rejection at submit (Wave 21): the {@code pinkId} the customer
 * carried does not entitle them to the campaign price — the slot is unknown,
 * foreign, failed/expired, already consumed by another order, for a different
 * product, or the quantity exceeds the campaign's per-user limit.
 *
 * <p>USER DECISION (2026-08-08): a stale slot is REJECTED with an honest message
 * ("this group has expired — start a new one or buy at regular price" flavor) and
 * the placement aborts — it must NEVER silently fall through to retail pricing.
 * Mirrors {@code LitemallInvalidCouponException}: thrown inside the transactional
 * {@code placeOrder}, rethrown typed by the orchestrator, mapped to a clean 422
 * submit-failed envelope by the REST layer (outside the doomed transaction).
 */
public class LitemallInvalidGroupSlotException extends RuntimeException {

    public LitemallInvalidGroupSlotException(String message) {
        super(message);
    }
}
