package org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment;

/**
 * The answer to "was this order actually paid?" — an explicit accept/reject plus a reason.
 *
 * <p>Deliberately not a bare boolean: the reason is what an operator needs when a customer
 * insists they paid, and what distinguishes "amount didn't match" (fraud signal) from
 * "Stripe unreachable" (retry). Constructed only via the factories, so a default-
 * initialised instance cannot read as accepted.
 *
 * <p>A rejection carries a {@link #isRetryable()} flag: {@link #unavailable(String)} means
 * the PSP could not be asked (transport, rate limit, 5xx) and the SAME question may
 * succeed later; {@link #rejected(String)} means the PSP answered and the answer was no.
 * The webhook path uses the flag to decide whether to keep or release its idempotency
 * claim on the event — burning the event id on an outage would make Stripe's resend a
 * no-op and let the order age into cancellation (plan-order-lifecycle-e2e.md, F2).
 */
public final class PaymentVerification {
    private final boolean verified;
    private final boolean retryable;
    private final String reason;

    private PaymentVerification(boolean verified, boolean retryable, String reason) {
        this.verified = verified;
        this.retryable = retryable;
        this.reason = reason;
    }

    public static PaymentVerification accepted() {
        return new PaymentVerification(true, false, null);
    }

    /** @param reason operator-facing detail; never rendered verbatim to the customer. */
    public static PaymentVerification rejected(String reason) {
        return new PaymentVerification(false, false, reason);
    }

    /** The PSP could not be asked. Still a rejection (fail closed) — but a retry may differ. */
    public static PaymentVerification unavailable(String reason) {
        return new PaymentVerification(false, true, reason);
    }

    public boolean isVerified() {
        return verified;
    }

    /** True only for {@link #unavailable(String)}: the rejection is a transport fact, not a verdict. */
    public boolean isRetryable() {
        return retryable;
    }

    /** Null when accepted. */
    public String getReason() {
        return reason;
    }
}
