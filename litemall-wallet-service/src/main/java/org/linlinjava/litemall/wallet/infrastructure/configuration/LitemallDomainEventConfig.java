package org.linlinjava.litemall.wallet.infrastructure.configuration;

import org.linlinjava.litemall.wallet.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.wallet.domain.events.LitemallSpringDomainEventPublisher;
import org.linlinjava.litemall.wallet.domain.service.wallet.LitemallWalletDomainService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LitemallDomainEventConfig {

    @Bean
    public LitemallDomainEventPublisher domainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new LitemallSpringDomainEventPublisher(applicationEventPublisher);
    }

    @Bean
    public LitemallWalletDomainService walletDomainService() {
        return new LitemallWalletDomainService();
    }
}
