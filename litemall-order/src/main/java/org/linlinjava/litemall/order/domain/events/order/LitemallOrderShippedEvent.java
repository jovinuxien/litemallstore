package org.linlinjava.litemall.order.domain.events.order;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

import java.time.LocalDateTime;

public class LitemallOrderShippedEvent extends LitemallDomainEvent {
    private final LitemallOrderId orderId;
    private final LocalDateTime occuredOn = LocalDateTime.now();

    public LitemallOrderShippedEvent(LitemallOrderId orderId) {
        //super("ORDER_SHIPPED");
        this.orderId = orderId;

    }
}
