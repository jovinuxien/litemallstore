package org.linlinjava.litemall.order.domain.model.events;

public interface LitemallDomainEventPublisher {
    void publish(LitemallDomainEvent event);

}
