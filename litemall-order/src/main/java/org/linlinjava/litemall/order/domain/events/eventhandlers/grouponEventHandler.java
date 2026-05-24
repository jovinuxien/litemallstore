package org.linlinjava.litemall.order.domain.events.eventhandlers;

import org.linlinjava.litemall.order.domain.events.groupon.LitemallGrouponParticipatedEvent;
import org.linlinjava.litemall.order.domain.events.groupon.LitemallGrouponSucceededEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class grouponEventHandler {

    private static final Logger log = LoggerFactory.getLogger(grouponEventHandler.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGrouponParticipated(LitemallGrouponParticipatedEvent event) {
        log.info("Groupon participated: grouponId={} orderId={} userId={} correlationId={}",
                event.getGrouponId(), event.getOrderId(),
                event.getParticipantUserId(), event.getCorrelationId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGrouponSucceeded(LitemallGrouponSucceededEvent event) {
        log.info("Groupon succeeded: grouponId={} correlationId={}",
                event.getGrouponId(), event.getCorrelationId());
    }
}
