package org.linlinjava.litemall.order.domain.events.cj;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.events.AbstractLitemallOrderDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

/**
 * CJ reported a PAID, placed order as CANCELLED on its side (plan-order-lifecycle-e2e.md,
 * F6 / decision D2). No money moves automatically — the refund stays a human decision —
 * but the customer is told, and ops are told, exactly once on the transition. Published
 * inside a transaction so the AFTER_COMMIT mail listener sees it.
 */
@Getter
public class LitemallCjFulfilmentCancelledEvent extends AbstractLitemallOrderDomainEvent {

    public static final int SCHEMA_VERSION = 1;

    private final LitemallOrderId orderId;
    private final String cjOrderId;

    public LitemallCjFulfilmentCancelledEvent(LitemallOrderId orderId, String cjOrderId) {
        super(SCHEMA_VERSION);
        this.orderId = orderId;
        this.cjOrderId = cjOrderId;
    }
}
