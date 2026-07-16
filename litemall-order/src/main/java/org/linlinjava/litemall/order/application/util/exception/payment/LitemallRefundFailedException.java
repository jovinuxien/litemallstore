package org.linlinjava.litemall.order.application.util.exception.payment;

/**
 * The PSP refused or could not be reached when reversing a charge (Wave 7, Task B).
 *
 * <p>Thrown so the surrounding transaction ROLLS BACK: the order stays in REFUND_REQUEST
 * and the aftersale stays open, i.e. the refund remains retryable and visible to an admin.
 * Before Wave 7 the card path only logged "would reverse" and returned the full amount, so
 * the order flipped to REFUNDED and {@code refund_amount} recorded money that never moved
 * — the customer's refund existed only in our database.
 *
 * <p>A refund that silently didn't happen is worse than one that visibly failed.
 *
 * <p>Caught at the REST layer, outside the transaction (the rollback-only→502 landmine).
 */
public class LitemallRefundFailedException extends RuntimeException {

    public LitemallRefundFailedException(String message) {
        super(message);
    }
}
