package org.linlinjava.litemall.promotion.infrastructure.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Selects which {@code CustomerStatisticsProvider} feeds the targeting engine,
 * bound from {@code litemall.promotion.stats.source}. The Phase-2 order-Feign
 * adapter stays the default; Phase 3 lets an operator swap in Matomo or merge
 * both without code changes — see {@link StatsSourceConfiguration}.
 */
@Component
@ConfigurationProperties(prefix = "litemall.promotion.stats")
@Getter
@Setter
public class StatsSourceProperties {

    /**
     * Active statistics source. {@code ORDER} = order-service RFM via Feign
     * (Phase-2 default, transactional truth); {@code MATOMO} = Matomo Reporting
     * API engagement; {@code COMPOSITE} = merge both by user id.
     */
    private Source source = Source.ORDER;

    public enum Source {
        ORDER,
        MATOMO,
        COMPOSITE
    }
}
