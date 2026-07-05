package org.linlinjava.litemall.order.domain.events.cj;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.events.AbstractLitemallOrderDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.cj.CjDisputeExpectation;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

/** A customer opened a CJ dispute for their dropship order (accepted by CJ). */
@Getter
public class LitemallCjDisputeOpenedEvent extends AbstractLitemallOrderDomainEvent {

    public static final int SCHEMA_VERSION = 1;

    private final LitemallOrderId orderId;
    private final String businessDisputeId;
    private final CjDisputeExpectation expectation;

    public LitemallCjDisputeOpenedEvent(LitemallOrderId orderId, String businessDisputeId,
                                        CjDisputeExpectation expectation) {
        super(SCHEMA_VERSION);
        this.orderId = orderId;
        this.businessDisputeId = businessDisputeId;
        this.expectation = expectation;
    }
}
