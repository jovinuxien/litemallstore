package org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment;

/**
 * The answer to "was this order actually paid?" — an explicit accept/reject plus a reason.
 *
 * <p>Deliberately not a bare boolean: the reason is what an operator needs when a customer
 * insists they paid, and what distinguishes "amount didn't match" (fraud signal) from
 * "Stripe unreachable" (retry). Constructed only via the factories, so a default-
 * initialised instance cannot read as accepted.
 */
public final class PaymentVerification {

    private final boolean verified;
    private final String reason;

    private PaymentVerification(boolean verified, String reason) {
        this.verified = verified;
        this.reason = reason;
    }

    public static PaymentVerification accepted() {
        return new PaymentVerification(true, null);
    }

    /** @param reason operator-facing detail; never rendered verbatim to the customer. */
    public static PaymentVerification rejected(String reason) {
        return new PaymentVerification(false, reason);
    }

    public boolean isVerified() {
        return verified;
    }

    /** Null when accepted. */
    public String getReason() {
        return reason;
    }
}
