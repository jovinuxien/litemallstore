package org.linlinjava.litemall.order.domain.model.events.order;

import org.linlinjava.litemall.order.domain.model.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

public class LitemallOrderPaidEvent extends LitemallDomainEvent {


    private final LitemallOrderId orderId;

    public LitemallOrderPaidEvent(LitemallOrderId orderId) {
        this.orderId = orderId;
    }


}
