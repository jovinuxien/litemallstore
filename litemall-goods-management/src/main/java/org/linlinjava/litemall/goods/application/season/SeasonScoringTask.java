package org.linlinjava.litemall.goods.application.season;

import java.util.List;

import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSeasonProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly seasonal scoring at 04:35 — after the 04:30 promo-candidate scorer and before the 04:45
 * search-stats rollup, so the three nightly passes do not contend for the same connections.
 *
 * <p>A nightly pass is not enough on its own and an arrival hook is not either. Wave 19 found the
 * deal scorer's arrival-only trigger left the standing catalogue unscored, which is why coupon
 * candidates needed a nightly batch as well; conversely a nightly-only pass makes a product that
 * arrives at 09:00 wait a day for a page it already qualifies for. Both run.
 */
@Component
public class SeasonScoringTask {

    private static final Logger log = LoggerFactory.getLogger(SeasonScoringTask.class);

    private final SeasonScoringService scoringService;
    private final LitemallSeasonProperties properties;

    public SeasonScoringTask(SeasonScoringService scoringService,
                             LitemallSeasonProperties properties) {
        this.scoringService = scoringService;
        this.properties = properties;
    }

    @Scheduled(cron = "${litemall.seasons.cron:0 35 4 * * *}")
    public void tick() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            List<SeasonScoringService.SeasonRunResult> results = scoringService.scoreAll();
            log.info("nightly season scoring finished: {} season(s)", results.size());
        } catch (RuntimeException ex) {
            // A scheduled pass that throws would stop the scheduler thread reporting anything
            // useful; the next night's run is the recovery path.
            log.error("nightly season scoring failed: {}", ex.toString(), ex);
        }
    }
}
