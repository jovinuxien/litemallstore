package org.linlinjava.litemall.order.infrastructure.configuration;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.events.LitemallSpringDomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LitemallDomainEventConfig {

    // For String Publisher

    @Bean
    public LitemallDomainEventPublisher domainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new LitemallSpringDomainEventPublisher(applicationEventPublisher);
    }
}
