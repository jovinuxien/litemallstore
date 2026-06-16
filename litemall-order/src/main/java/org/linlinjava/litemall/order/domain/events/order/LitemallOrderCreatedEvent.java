package org.linlinjava.litemall.order.domain.events.order;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.events.AbstractLitemallOrderDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

@Getter
public class LitemallOrderCreatedEvent extends AbstractLitemallOrderDomainEvent {

    public static final int SCHEMA_VERSION = 1;

    private final LitemallOrderId orderId;
    private final LitemallMoney orderAmount;
    private final Integer userId;
    private final String orderSn;

    public LitemallOrderCreatedEvent(LitemallOrderId orderId, LitemallMoney orderAmount, Integer userId, String orderSn) {
        super(SCHEMA_VERSION);
        this.orderId = orderId;
        this.orderAmount = orderAmount;
        this.userId = userId;
        this.orderSn = orderSn;
    }
}
