package org.linlinjava.litemall.order.domain.events.order;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

public class LitemallOrderCancelledEvent extends LitemallDomainEvent {

    private final LitemallOrderId orderId;
    private final String cancelReason;
    //private final LitemallOrderStatus previousStatus;

    //public LitemallOrderCancelledEvent(LitemallOrderId orderId, String cancelReason, LitemallOrderStatus previousStatus) {
    public LitemallOrderCancelledEvent(LitemallOrderId orderId, String cancelReason) {
        //super("ORDER_CANCELLED");
        this.orderId = orderId;
        this.cancelReason = cancelReason;
        //this.previousStatus = previousStatus;
    }
}
