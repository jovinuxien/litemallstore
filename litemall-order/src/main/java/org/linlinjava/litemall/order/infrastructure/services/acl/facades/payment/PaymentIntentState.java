package org.linlinjava.litemall.order.infrastructure.services.acl.facades.payment;

/**
 * What the PSP currently says about a PaymentIntent — the answer to "is there money in
 * flight for this order?" that the unpaid-order sweep and the stray-payment path need
 * before they touch an order (plan-order-lifecycle-e2e.md, package A).
 *
 * <p>Deliberately coarser than Stripe's status vocabulary: the callers only ever branch
 * on these five. {@link Status#UNAVAILABLE} is "we could not ask" and is NOT a statement
 * about the intent — a caller that cannot tell must defer, never cancel.
 */
public final class PaymentIntentState {

    public enum Status {
        /** Funds captured. Whoever holds this intent's order has been paid. */
        SUCCEEDED,
        /** Asynchronous method in flight (SEPA debit, some bank redirects): may still succeed days later. */
        PROCESSING,
        /** Not attempted or abandoned (requires_payment_method / _confirmation / _action). Safe to cancel. */
        PENDING,
        /** Cancelled at the PSP: can never capture. */
        CANCELED,
        /** PSP unreachable / no PSP configured / unparseable answer. Not knowledge. */
        UNAVAILABLE
    }

    private final Status status;
    /** {@code metadata.orderId} as the PSP holds it; null when absent or unavailable. */
    private final Integer orderId;
    /** {@code amount_received} in minor units; 0 unless SUCCEEDED. */
    private final long amountReceivedMinor;
    private final String currency;
    /** Operator-facing detail (raw PSP status, error text). Never rendered to a customer. */
    private final String detail;

    private PaymentIntentState(Status status, Integer orderId, long amountReceivedMinor,
                               String currency, String detail) {
        this.status = status;
        this.orderId = orderId;
        this.amountReceivedMinor = amountReceivedMinor;
        this.currency = currency;
        this.detail = detail;
    }

    public static PaymentIntentState of(Status status, Integer orderId, long amountReceivedMinor,
                                        String currency, String detail) {
        return new PaymentIntentState(status, orderId, amountReceivedMinor, currency, detail);
    }

    public static PaymentIntentState unavailable(String detail) {
        return new PaymentIntentState(Status.UNAVAILABLE, null, 0L, null, detail);
    }

    public Status getStatus() {
        return status;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public long getAmountReceivedMinor() {
        return amountReceivedMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDetail() {
        return detail;
    }

    public boolean is(Status expected) {
        return status == expected;
    }

    /** True when the PSP reports captured funds tagged with {@code orderId}. */
    public boolean isSucceededFor(Integer expectedOrderId) {
        return status == Status.SUCCEEDED && orderId != null && orderId.equals(expectedOrderId);
    }

    @Override
    public String toString() {
        return "PaymentIntentState{" + status + ", orderId=" + orderId + ", received=" + amountReceivedMinor
                + " " + currency + (detail == null ? "" : ", " + detail) + "}";
    }
}
