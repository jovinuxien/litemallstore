package org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment;

/**
 * A server-created PaymentIntent handed to the SPA so it can confirm with Stripe Elements.
 *
 * <p>Carries the client secret, never the API secret key. The amount is echoed in minor
 * units purely so the SPA can display what it is about to confirm — it is not an input to
 * anything: the server created the intent from the order and re-asserts the amount at
 * verification.
 */
public final class PaymentIntentDraft {

    private final String paymentIntentId;
    private final String clientSecret;
    private final long amountMinor;
    private final String currency;

    public PaymentIntentDraft(String paymentIntentId, String clientSecret, long amountMinor, String currency) {
        this.paymentIntentId = paymentIntentId;
        this.clientSecret = clientSecret;
        this.amountMinor = amountMinor;
        this.currency = currency;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }
}
