package org.linlinjava.litemall.promotion.domain.service.targeting;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.AudienceMember;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerSegment;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerStatistics;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.RfmScore;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.TargetingCriteria;
import org.linlinjava.litemall.promotion.infrastructure.configuration.PromotionTargetingProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit test for the Phase-2 targeting engine — RFM scoring + segmentation +
 * rule-based audience selection — against the documented default thresholds. No
 * Spring context, broker, or DB; deterministic given a fixed {@code now}.
 */
class TargetingEngineTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 2, 12, 0);

    private final PromotionTargetingProperties props = new PromotionTargetingProperties();
    private final RfmScoringService scoring = new RfmScoringService(props);
    private final SegmentationService segmentation = new SegmentationService(props);
    private final RuleBasedAudienceSelector selector = new RuleBasedAudienceSelector(scoring, segmentation);

    private CustomerStatistics stats(int userId, long daysSinceOrder, int orders, String spend) {
        return new CustomerStatistics(
                new LitemallUserId(userId),
                NOW.minusDays(daysSinceOrder),
                orders,
                new LitemallMoney(new BigDecimal(spend)));
    }

    @Test
    void scoresRfmFromConfiguredBoundaries() {
        // Recency ≤30d → 5; Frequency ≥12 → 5; Monetary ≥5000 → 5.
        RfmScore best = scoring.score(stats(1, 5, 15, "6000"), NOW);
        assertEquals(new RfmScore(5, 5, 5), best);

        // Recency >240d → 1; Frequency <2 → 1; Monetary <100 → 1.
        RfmScore worst = scoring.score(stats(2, 300, 1, "50"), NOW);
        assertEquals(new RfmScore(1, 1, 1), worst);

        // Mid: 90d → R3 (exceeds 30,60); 5 orders → F3 (≥2,4); 600 spend → M3 (≥100,500).
        RfmScore mid = scoring.score(stats(3, 90, 5, "600"), NOW);
        assertEquals(new RfmScore(3, 3, 3), mid);
    }

    @Test
    void classifiesSegmentsByRule() {
        assertEquals(CustomerSegment.CHAMPIONS, segmentation.classify(new RfmScore(5, 5, 5)));
        // Lapsed but historically frequent → AT_RISK (R≤2, F≥3).
        assertEquals(CustomerSegment.AT_RISK, segmentation.classify(new RfmScore(1, 5, 4)));
        // Frequent + valuable, not freshest → LOYAL (F,M≥3).
        assertEquals(CustomerSegment.LOYAL, segmentation.classify(new RfmScore(3, 3, 3)));
        // Recent first-time low-frequency buyer → NEW (R≥4, F≤1).
        assertEquals(CustomerSegment.NEW, segmentation.classify(new RfmScore(5, 1, 1)));
        // Long inactive, low engagement → HIBERNATING.
        assertEquals(CustomerSegment.HIBERNATING, segmentation.classify(new RfmScore(2, 2, 2)));
    }

    @Test
    void selectorKeepsOnlyMatchingSegment() {
        List<CustomerStatistics> population = Arrays.asList(
                stats(1, 5, 15, "6000"),   // CHAMPIONS
                stats(2, 300, 10, "2000"), // AT_RISK
                stats(3, 300, 1, "50"));   // HIBERNATING

        TargetingCriteria championsOnly = new TargetingCriteria(
                EnumSet.of(CustomerSegment.CHAMPIONS), null, null, null);

        List<AudienceMember> audience = selector.select(championsOnly, population, NOW);

        assertEquals(1, audience.size());
        assertEquals(1, audience.get(0).getUserId().getId());
        assertEquals(CustomerSegment.CHAMPIONS, audience.get(0).getSegment());
    }

    @Test
    void scoreFloorGatesAudienceBeyondSegment() {
        List<CustomerStatistics> population = Arrays.asList(
                stats(1, 5, 15, "6000"),   // R5 F5 M5
                stats(2, 90, 5, "600"));   // R3 F3 M3

        // Any segment, but require monetary score ≥ 4 — only the first qualifies.
        TargetingCriteria highValue = new TargetingCriteria(
                EnumSet.noneOf(CustomerSegment.class), null, null, 4);

        List<AudienceMember> audience = selector.select(highValue, population, NOW);

        assertEquals(1, audience.size());
        assertTrue(audience.get(0).getScore().getMonetary() >= 4);
    }
}
