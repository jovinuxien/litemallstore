package org.linlinjava.litemall.order.domain.events;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

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
