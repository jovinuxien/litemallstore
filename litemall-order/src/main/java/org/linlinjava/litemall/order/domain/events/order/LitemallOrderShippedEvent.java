package org.linlinjava.litemall.order.domain.events.order;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.events.AbstractLitemallOrderDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

@Getter
public class LitemallOrderShippedEvent extends AbstractLitemallOrderDomainEvent {

    public static final int SCHEMA_VERSION = 1;

    private final LitemallOrderId orderId;

    public LitemallOrderShippedEvent(LitemallOrderId orderId) {
        super(SCHEMA_VERSION);
        this.orderId = orderId;
    }
}
