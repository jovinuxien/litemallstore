package org.linlinjava.litemall.order.infrastructure.configuration;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.events.LitemallSpringDomainEventPublisher;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderDomainService;
import org.linlinjava.litemall.order.domain.service.wallet.LitemallWalletDomainService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration("orderDomainEventConfig")
public class LitemallDomainEventConfig {

    // Order's own Spring publisher (order.domain.events type). Distinct config and
    // bean names from litemall-core's LitemallDomainEventConfig#domainEventPublisher
    // so both coexist once the order package is component-scanned.

    @Bean
    public LitemallDomainEventPublisher orderDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new LitemallSpringDomainEventPublisher(applicationEventPublisher);
    }

    // Wallet vertical: pure domain service (absorbed from litemall-wallet-service)
    @Bean
    public LitemallWalletDomainService walletDomainService() {
        return new LitemallWalletDomainService();
    }

    // Order pricing/stock-validation domain service (stateless; deps passed per call)
    @Bean
    public LitemallOrderDomainService orderDomainService() {
        return new LitemallOrderDomainService();
    }
}
