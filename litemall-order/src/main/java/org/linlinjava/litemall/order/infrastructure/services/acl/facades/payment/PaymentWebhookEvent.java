package org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment;

/**
 * A signature-verified Stripe webhook event, reduced to what the order module acts on.
 *
 * <p>Only ever constructed AFTER the signature check passes — an instance of this type
 * means "Stripe really sent this". {@code eventId} is the idempotency key recorded in
 * {@code litemall_stripe_event}; Stripe retries deliveries, so the same event id arrives
 * repeatedly and must be processed at most once.
 *
 * <p>{@code orderId} comes from the intent's metadata and is therefore only as trustworthy
 * as what we put there at creation — which is why the paid path re-verifies the amount
 * against the order rather than trusting this figure.
 */
public final class PaymentWebhookEvent {

    private final String eventId;
    private final String type;
    private final String paymentIntentId;
    private final Integer orderId;
    private final long amountReceivedMinor;
    private final String currency;

    public PaymentWebhookEvent(String eventId, String type, String paymentIntentId,
                               Integer orderId, long amountReceivedMinor, String currency) {
        this.eventId = eventId;
        this.type = type;
        this.paymentIntentId = paymentIntentId;
        this.orderId = orderId;
        this.amountReceivedMinor = amountReceivedMinor;
        this.currency = currency;
    }

    public String getEventId() {
        return eventId;
    }

    /** e.g. payment_intent.succeeded | payment_intent.payment_failed. */
    public String getType() {
        return type;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    /** From metadata.orderId; null when absent or unparseable (e.g. an intent we didn't create). */
    public Integer getOrderId() {
        return orderId;
    }

    public long getAmountReceivedMinor() {
        return amountReceivedMinor;
    }

    public String getCurrency() {
        return currency;
    }
}
