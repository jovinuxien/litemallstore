package org.linlinjava.litemall.order.domain.model.events.order;

import org.linlinjava.litemall.order.domain.model.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallOrderId;

import java.time.LocalDateTime;

public class LitemallOrderCanceledEvent {

    private final LitemallOrderId orderId;
    private final String reason;
    private final LocalDateTime occurredOn = LocalDateTime.now();

    public LitemallOrderCanceledEvent(LitemallOrderId orderId, String reason) {
        this.orderId = orderId;
        this.reason = reason;
    }
}
