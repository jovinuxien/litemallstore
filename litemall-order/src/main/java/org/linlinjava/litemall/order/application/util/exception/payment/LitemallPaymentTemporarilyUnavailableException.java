package org.linlinjava.litemall.order.application.util.exception.payment;

/**
 * The PSP could not be asked RIGHT NOW (transport failure, rate limit, 5xx) while
 * answering a question whose answer decides money — so the caller must retry, not decide.
 *
 * <p>Thrown out of the orchestrator's transaction on purpose: rolling that transaction back
 * releases the webhook's idempotency claim on the Stripe event, and the REST layer answers
 * 503 so Stripe redelivers with its own backoff. Before this existed, a transient outage
 * during verification committed the claim and returned 200, after which the event id was
 * dead and the order aged into SYSTEM_CANCELED (plan-order-lifecycle-e2e.md, F2).
 *
 * <p>A subclass of {@link LitemallPaymentGatewayException} so every existing catch that
 * turns a gateway problem into a typed pay-failed envelope keeps doing so; the webhook
 * controller catches this subclass FIRST to answer 503 instead of 400.
 */
public class LitemallPaymentTemporarilyUnavailableException extends LitemallPaymentGatewayException {
    public LitemallPaymentTemporarilyUnavailableException(String message) {
        super(message);
    }
}
