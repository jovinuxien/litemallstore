package org.linlinjava.litemall.order.domain.events.order;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

public class LitemallOrderPaidEvent extends LitemallDomainEvent {


    private final LitemallOrderId orderId;

    public LitemallOrderPaidEvent(LitemallOrderId orderId) {
        //super("ORDER_PAID");
        this.orderId = orderId;
    }


}
