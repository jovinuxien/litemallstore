package org.linlinjava.litemall.order.domain.events.order;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.events.AbstractLitemallOrderDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

/**
 * Raised when a customer opens a refund/return on a paid or shipped order
 * (transition to REFUND_REQUEST). Awaits admin approval.
 */
@Getter
public class LitemallOrderRefundRequestedEvent extends AbstractLitemallOrderDomainEvent {

    public static final int SCHEMA_VERSION = 1;

    private final LitemallOrderId orderId;
    private final String reason;

    public LitemallOrderRefundRequestedEvent(LitemallOrderId orderId, String reason) {
        super(SCHEMA_VERSION);
        this.orderId = orderId;
        this.reason = reason;
    }
}
