package org.linlinjava.litemall.promotion.infrastructure.scheduling;

import org.linlinjava.litemall.promotion.application.internal.LitemallSocialPostServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Wave-6 opt-in auto-poster: polls the flash-deal price-swap state
 * (goods-management's V38 lifecycle) and posts newly activated deals to every
 * ENABLED social platform. Doubly gated — the tick no-ops unless
 * {@code litemall.promotion.social.auto-post-deals=true}, and each platform
 * additionally needs its own {@code enabled} flag. Dedupe is restart-safe DB
 * state (armed ledger rows), never in-memory — see
 * {@link LitemallSocialPostServiceImpl#autoPostTick()} and
 * {@code docs/adr-social-publishing.md}. Cadence from
 * {@code litemall.promotion.social.auto-post-sweep-ms} (PromotionExpirySweeper
 * pattern).
 */
@Component
public class SocialDealAutoPoster {

    private static final Logger logger = LoggerFactory.getLogger(SocialDealAutoPoster.class);

    private final LitemallSocialPostServiceImpl socialPostService;

    public SocialDealAutoPoster(LitemallSocialPostServiceImpl socialPostService) {
        this.socialPostService = socialPostService;
    }

    @Scheduled(fixedDelayString = "${litemall.promotion.social.auto-post-sweep-ms:60000}")
    public void sweep() {
        try {
            socialPostService.autoPostTick();
        } catch (Exception e) {
            // Never let one bad tick kill the schedule.
            logger.error("Social deal auto-post sweep failed", e);
        }
    }
}
