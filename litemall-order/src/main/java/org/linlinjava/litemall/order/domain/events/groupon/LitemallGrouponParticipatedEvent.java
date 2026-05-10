package org.linlinjava.litemall.order.domain.model.events.groupon;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

import java.time.LocalDateTime;

public class LitemallGrouponParticipatedEvent extends LitemallDomainEvent {

    private final LitemallGrouponId grouponId;
    private final LitemallOrderId orderId;
    private final Integer participantUserId;
    private final LocalDateTime participationTime;

    public LitemallGrouponParticipatedEvent(LitemallGrouponId grouponId, LitemallOrderId orderId,
                                            Integer participantUserId, LocalDateTime participationTime) {
        super("GROUPON_PARTICIPATED");
        this.grouponId = grouponId;
        this.orderId = orderId;
        this.participantUserId = participantUserId;
        this.participationTime = participationTime;
    }
}
