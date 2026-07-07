package org.linlinjava.litemall.promotion.infrastructure.configuration;

import org.linlinjava.litemall.promotion.application.ports.CustomerStatisticsProvider;
import org.linlinjava.litemall.promotion.infrastructure.acl.matomo.MatomoStatisticsAdapter;
import org.linlinjava.litemall.promotion.infrastructure.services.acl.CompositeCustomerStatisticsProvider;
import org.linlinjava.litemall.promotion.infrastructure.services.acl.OrderStatisticsAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Wires the active {@link CustomerStatisticsProvider} feeding the targeting
 * engine from {@code litemall.promotion.stats.source} (Phase 3). Both the
 * order-Feign adapter and the Matomo adapter remain Spring beans; this factory
 * picks (or merges) them and marks the result {@link Primary} so callers —
 * {@code LitemallCampaignServiceImpl} — inject the chosen provider unchanged.
 *
 * <p>Default {@code ORDER} preserves the Phase-2 behaviour; {@code MATOMO} swaps
 * in analytics-derived stats; {@code COMPOSITE} merges both by user id.
 */
@Configuration
public class StatsSourceConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(StatsSourceConfiguration.class);

    @Bean
    @Primary
    public CustomerStatisticsProvider customerStatisticsProvider(
            OrderStatisticsAdapter orderStatisticsAdapter,
            MatomoStatisticsAdapter matomoStatisticsAdapter,
            StatsSourceProperties properties) {
        StatsSourceProperties.Source source = properties.getSource();
        logger.info("Customer statistics source = {}", source);
        switch (source) {
            case MATOMO:
                return matomoStatisticsAdapter;
            case COMPOSITE:
                return new CompositeCustomerStatisticsProvider(orderStatisticsAdapter, matomoStatisticsAdapter);
            case ORDER:
            default:
                return orderStatisticsAdapter;
        }
    }
}
