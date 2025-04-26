package org.linlinjava.litemall.core.events;

public interface LitemallDomainEventPublisher {
    void publish(LitemallDomainEvent event);
}
