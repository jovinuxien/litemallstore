package org.linlinjava.litemall.order.domain.events.cj;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.events.AbstractLitemallOrderDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

/** The customer withdrew a CJ dispute (cancelled at CJ too). */
@Getter
public class LitemallCjDisputeCancelledEvent extends AbstractLitemallOrderDomainEvent {

    public static final int SCHEMA_VERSION = 1;

    private final LitemallOrderId orderId;
    private final String businessDisputeId;

    public LitemallCjDisputeCancelledEvent(LitemallOrderId orderId, String businessDisputeId) {
        super(SCHEMA_VERSION);
        this.orderId = orderId;
        this.businessDisputeId = businessDisputeId;
    }
}
