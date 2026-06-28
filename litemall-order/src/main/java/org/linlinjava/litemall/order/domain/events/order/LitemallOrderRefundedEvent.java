package org.linlinjava.litemall.order.domain.events.order;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.events.AbstractLitemallOrderDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

/**
 * Raised when an admin approves a refund and the order reaches REFUNDED
 * (the money has been returned, e.g. credited back to the wallet).
 */
@Getter
public class LitemallOrderRefundedEvent extends AbstractLitemallOrderDomainEvent {

    public static final int SCHEMA_VERSION = 1;

    private final LitemallOrderId orderId;

    public LitemallOrderRefundedEvent(LitemallOrderId orderId) {
        super(SCHEMA_VERSION);
        this.orderId = orderId;
    }
}
