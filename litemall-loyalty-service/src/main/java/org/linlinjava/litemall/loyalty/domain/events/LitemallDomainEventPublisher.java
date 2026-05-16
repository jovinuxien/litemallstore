package org.linlinjava.litemall.loyalty.domain.events;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;

public interface LitemallDomainEventPublisher {
    void publish(LitemallDomainEvent event);
}
