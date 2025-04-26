package org.linlinjava.litemall.core.config;

import org.linlinjava.litemall.core.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.core.events.LitemallSpringDomainEventPublisher;
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
