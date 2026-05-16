package org.linlinjava.litemall.wallet.domain.events;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;

public interface LitemallDomainEventPublisher {

    void publish(LitemallDomainEvent event);
}
