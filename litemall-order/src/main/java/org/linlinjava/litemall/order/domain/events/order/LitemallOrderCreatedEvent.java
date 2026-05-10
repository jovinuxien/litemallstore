package org.linlinjava.litemall.order.domain.model.events.order;

import lombok.Getter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

@Getter
public class LitemallOrderCreatedEvent extends LitemallDomainEvent {


    private final LitemallOrderId orderId;
    private final LitemallMoney orderAmount;
    private final Integer userId;
    private final String orderSn;
    private final LitemallOrder order;

    private LitemallOrderCreatedEvent(LitemallOrderId orderId, LitemallMoney orderAmount, Integer userId, String orderSn, LitemallOrder order) {
        super("ORDER_CREATED");
        this.orderId = orderId;
        this.orderAmount = orderAmount;
        this.userId = userId;
        this.orderSn = orderSn;
        this.order = order;
    }

}
