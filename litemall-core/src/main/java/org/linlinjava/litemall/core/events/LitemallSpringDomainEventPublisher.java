package org.linlinjava.litemall.core.events;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

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
