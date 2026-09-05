package org.linlinjava.litemall.order.domain.events.payment;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.events.AbstractLitemallOrderDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

import java.math.BigDecimal;

/**
 * A charge landed on an order that could no longer accept it — cancelled by the unpaid
 * sweep before an asynchronous payment settled, or already paid through another intent —
 * and the store reversed it at the PSP automatically (plan-order-lifecycle-e2e.md, D1).
 *
 * <p>Published AFTER the refund succeeded, inside the transaction that records it, so the
 * AFTER_COMMIT mail listener tells the customer exactly once. Not forwarded to the broker
 * (the outbox keeps it as an audit row).
 */
@Getter
public class LitemallStrayPaymentRefundedEvent extends AbstractLitemallOrderDomainEvent {

    public static final int SCHEMA_VERSION = 1;

    private final LitemallOrderId orderId;
    private final String paymentIntentId;
    private final BigDecimal amount;
    private final String refundId;
    /** Order status at the time of the refund (e.g. SYSTEM_CANCELED, PAID) — the reason the charge was stray. */
    private final String orderStatus;

    public LitemallStrayPaymentRefundedEvent(LitemallOrderId orderId, String paymentIntentId,
                                             BigDecimal amount, String refundId, String orderStatus) {
        super(SCHEMA_VERSION);
        this.orderId = orderId;
        this.paymentIntentId = paymentIntentId;
        this.amount = amount;
        this.refundId = refundId;
        this.orderStatus = orderStatus;
    }
}
