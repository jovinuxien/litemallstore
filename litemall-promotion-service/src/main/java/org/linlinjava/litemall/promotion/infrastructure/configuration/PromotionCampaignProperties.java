package org.linlinjava.litemall.promotion.infrastructure.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed, environment-overridable settings for the Wave-12 campaign schedule
 * tick. Bound from {@code litemall.promotion.campaign.*}.
 *
 * <p>Unlike the social adapters, the scheduler defaults ON: it only executes
 * schedules an admin already put on a campaign (campaigns without a start time
 * are never touched), so the flag is an ops off-switch, not an opt-in.
 */
@Component
@ConfigurationProperties(prefix = "litemall.promotion.campaign")
@Getter
@Setter
public class PromotionCampaignProperties {

    /** Ops off-switch for the schedule tick (activation + draft publishing + completion). */
    private boolean schedulerEnabled = true;

    /** Tick cadence; state-based, so a slow cadence only delays transitions. */
    private long tickMs = 60_000L;
}
