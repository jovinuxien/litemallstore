package org.linlinjava.litemall.order.domain.events.groupon;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.events.AbstractLitemallOrderDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

import java.time.LocalDateTime;

@Getter
public class LitemallGrouponParticipatedEvent extends AbstractLitemallOrderDomainEvent {

    public static final int SCHEMA_VERSION = 1;

    private final LitemallGrouponId grouponId;
    private final LitemallOrderId orderId;
    private final Integer participantUserId;
    private final LocalDateTime participationTime;

    public LitemallGrouponParticipatedEvent(LitemallGrouponId grouponId, LitemallOrderId orderId,
                                            Integer participantUserId, LocalDateTime participationTime) {
        super(SCHEMA_VERSION);
        this.grouponId = grouponId;
        this.orderId = orderId;
        this.participantUserId = participantUserId;
        this.participationTime = participationTime;
    }
}
