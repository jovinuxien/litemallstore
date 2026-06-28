package org.linlinjava.litemall.order.domain.events.order;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.events.AbstractLitemallOrderDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

/**
 * Raised when an order reaches DELIVERED (customer confirmed receipt) or
 * AUTO_DELIVERED (system auto-confirm after the grace window).
 */
@Getter
public class LitemallOrderDeliveredEvent extends AbstractLitemallOrderDomainEvent {

    public static final int SCHEMA_VERSION = 1;

    private final LitemallOrderId orderId;
    /** true when the system auto-confirmed rather than the customer. */
    private final boolean autoConfirmed;

    public LitemallOrderDeliveredEvent(LitemallOrderId orderId, boolean autoConfirmed) {
        super(SCHEMA_VERSION);
        this.orderId = orderId;
        this.autoConfirmed = autoConfirmed;
    }
}
