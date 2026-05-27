package org.linlinjava.litemall.order.domain.events;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * In-process domain-event publisher. Cross-process forwarding (Kafka) is
 * handled by {@link org.linlinjava.litemall.order.domain.events.eventhandlers
 * .LitemallKafkaDomainEventForwarder} via
 * {@code @TransactionalEventListener(AFTER_COMMIT)} so that no event is
 * shipped if the surrounding transaction rolls back. A DB outbox would offer
 * a stronger guarantee (no broker write loss after commit) — deferred per the
 * CLAUDE.md order-worktree "stretch" note. AFTER_COMMIT is the chosen
 * trade-off until the outbox lands.
 */
@Component
public class LitemallSpringDomainEventPublisher implements LitemallDomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    public LitemallSpringDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }
    @Override
    public void publish(LitemallDomainEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
