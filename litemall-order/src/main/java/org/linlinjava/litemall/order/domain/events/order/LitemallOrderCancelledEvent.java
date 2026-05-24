package org.linlinjava.litemall.order.domain.events.order;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.events.AbstractLitemallOrderDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

@Getter
public class LitemallOrderCancelledEvent extends AbstractLitemallOrderDomainEvent {

    public static final int SCHEMA_VERSION = 1;

    private final LitemallOrderId orderId;
    private final String cancelReason;

    public LitemallOrderCancelledEvent(LitemallOrderId orderId, String cancelReason) {
        super(SCHEMA_VERSION);
        this.orderId = orderId;
        this.cancelReason = cancelReason;
    }
}
