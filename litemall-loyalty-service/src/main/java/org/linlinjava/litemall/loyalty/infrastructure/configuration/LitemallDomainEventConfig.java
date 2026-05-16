package org.linlinjava.litemall.loyalty.infrastructure.configuration;

import org.linlinjava.litemall.loyalty.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.loyalty.domain.events.LitemallSpringDomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LitemallDomainEventConfig {

    @Bean
    public LitemallDomainEventPublisher domainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new LitemallSpringDomainEventPublisher(applicationEventPublisher);
    }
}
