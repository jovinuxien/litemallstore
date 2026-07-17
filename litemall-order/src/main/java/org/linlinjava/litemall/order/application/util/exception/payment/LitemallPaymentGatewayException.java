package org.linlinjava.litemall.order.application.util.exception.payment;

/**
 * The PSP could not be asked, or answered in a way we must not paper over: Stripe
 * disabled/misconfigured, unreachable, or a webhook whose signature does not verify.
 *
 * <p>Distinct from a payment being REJECTED (that is a {@code PaymentVerification} with a
 * reason, not an exception). This is "we cannot honestly say what happened" — so it is
 * surfaced, never swallowed into a fake success.
 *
 * <p>Like the rest of the family, RETHROWN through the orchestrator and caught at the REST
 * layer — never converted inside the transaction (the rollback-only→502 landmine).
 */
public class LitemallPaymentGatewayException extends RuntimeException {

    public LitemallPaymentGatewayException(String message) {
        super(message);
    }

    public LitemallPaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
