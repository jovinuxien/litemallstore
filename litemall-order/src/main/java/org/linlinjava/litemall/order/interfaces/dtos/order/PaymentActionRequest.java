package org.linlinjava.litemall.order.interfaces.dtos.order;

import org.linlinjava.litemall.order.domain.model.valueobjects.enums.payment.PaymentMethod;

/**
 * Request body for {@code POST /srv/order/{orderId}/actions/pay}.
 *
 * <p>Identity ({@code userId}) and the target {@code orderId} are NOT carried here —
 * they come from the {@code X-User-Id} header and the path, as with cancel/submit.
 *
 * <ul>
 *   <li>{@code paymentMethod} — the selected domain {@link PaymentMethod}
 *       (e.g. {@code WALLET}, {@code CREDIT_CARD}).</li>
 *   <li>{@code paymentIntentId} — for the CARD path, the client-confirmed Stripe
 *       PaymentIntent id (client-confirmed boundary). Omitted / ignored for WALLET.</li>
 * </ul>
 */
public class PaymentActionRequest {

    private PaymentMethod paymentMethod;
    private String paymentIntentId;

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public void setPaymentIntentId(String paymentIntentId) {
        this.paymentIntentId = paymentIntentId;
    }
}
