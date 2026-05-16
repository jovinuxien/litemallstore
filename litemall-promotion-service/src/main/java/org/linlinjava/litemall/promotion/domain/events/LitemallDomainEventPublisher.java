package org.linlinjava.litemall.promotion.domain.events;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;

public interface LitemallDomainEventPublisher {
    void publish(LitemallDomainEvent event);
}
