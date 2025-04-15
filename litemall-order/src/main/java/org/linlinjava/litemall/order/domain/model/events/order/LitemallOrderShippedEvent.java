package org.linlinjava.litemall.order.domain.model.events.order;

import org.linlinjava.litemall.order.domain.model.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

import java.time.LocalDateTime;

public class LitemallOrderShippedEvent extends LitemallDomainEvent {
    private final LitemallOrderId orderId;
    private final LocalDateTime occuredOn = LocalDateTime.now();

    public LitemallOrderShippedEvent(LitemallOrderId orderId) {
        this.orderId = orderId;
    }
}
