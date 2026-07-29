package org.linlinjava.litemall.promotion.infrastructure.scheduling;

import org.linlinjava.litemall.promotion.application.internal.LitemallCampaignSchedulingServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Wave-12 campaign schedule tick: activates due DRAFT campaigns (activate +
 * evaluate + fire their unpublished social drafts) and completes ACTIVE
 * campaigns past their end time. Gated by
 * {@code litemall.promotion.campaign.scheduler-enabled} — the guard lives in
 * {@link LitemallCampaignSchedulingServiceImpl#scheduleTick()}
 * (SocialDealAutoPoster pattern). Cadence from
 * {@code litemall.promotion.campaign.tick-ms}. Like the other schedulers here,
 * NO leader election — see the replicas:1 banner in docker-compose.prod.yml.
 */
@Component
public class CampaignScheduleTick {

    private static final Logger logger = LoggerFactory.getLogger(CampaignScheduleTick.class);

    private final LitemallCampaignSchedulingServiceImpl schedulingService;

    public CampaignScheduleTick(LitemallCampaignSchedulingServiceImpl schedulingService) {
        this.schedulingService = schedulingService;
    }

    @Scheduled(fixedDelayString = "${litemall.promotion.campaign.tick-ms:60000}")
    public void tick() {
        try {
            schedulingService.scheduleTick();
        } catch (Exception e) {
            // Never let one bad tick kill the schedule.
            logger.error("Campaign schedule tick failed", e);
        }
    }
}
