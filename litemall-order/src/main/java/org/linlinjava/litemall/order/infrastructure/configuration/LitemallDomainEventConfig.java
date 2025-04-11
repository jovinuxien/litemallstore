package org.linlinjava.litemall.order.infrastructure.configuration;


import org.linlinjava.litemall.order.domain.model.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.model.events.LitemallSpringDomainEventPublisher;
import org.springframework.cloud.util.ConditionalOnBootstrapEnabled;
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
