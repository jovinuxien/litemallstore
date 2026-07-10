package org.linlinjava.litemall.promotion.infrastructure.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed, environment-overridable tuning for group-buy participation — no magic
 * numbers in the pink/sweep logic. Bound from
 * {@code litemall.promotion.groupon.*}; defaults are the documented dev
 * baseline.
 */
@Component
@ConfigurationProperties(prefix = "litemall.promotion.groupon")
@Getter
@Setter
public class PromotionGrouponProperties {

    /**
     * How long a newly started group has to reach its headcount, in hours
     * (crmeb {@code effectiveTime}). The group's expire time is additionally
     * capped at the campaign's end time.
     */
    private int groupTtlHours = 24;

    /** Delay between expiry-sweep runs, in milliseconds. */
    private long sweepFixedDelayMs = 60_000L;
}
