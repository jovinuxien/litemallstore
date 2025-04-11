package org.linlinjava.litemall.order.domain.model.events.order;

import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.order.domain.model.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallOrderId;

public class LitemallOrderCreatedEvent extends LitemallDomainEvent {

    private final LitemallOrder order;

    private LitemallOrderCreatedEvent(LitemallOrder order) {
     this.order = order;
    }

    public LitemallOrder getOrder(){
        return order;
    }
}
