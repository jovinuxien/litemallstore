package org.linlinjava.litemall.goods.application.season;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallSeasonCandidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gates and the curve. These pin the three decisions that would be silent failures rather
 * than visible ones: an uncosted product must not pass, freshness must never be able to zero a
 * score, and an unknown tier must not publish.
 */
public class SeasonCandidateScorerTest {

    private static final BigDecimal FLOOR = new BigDecimal("5.00");
    private static final BigDecimal CEILING = new BigDecimal("500.00");

    private static LitemallGoods goods(String retail, boolean onSale) {
        LitemallGoods goods = new LitemallGoods();
        goods.setId(42);
        goods.setName("Chunky Knit Throw");
        goods.setRetailPrice(new BigDecimal(retail));
        goods.setIsOnSale(onSale);
        goods.setDeleted(false);
        goods.setAddTime(LocalDateTime.now().minusDays(5));
        return goods;
    }

    // ---- the gates ---------------------------------------------------------

    @Test
    public void aProfitableInStockProductPasses() {
        assertEquals(SeasonCandidateScorer.Rejection.NONE,
                SeasonCandidateScorer.gate(goods("30.00", true), new BigDecimal("12.00"), 20,
                        FLOOR, CEILING, null, null));
    }

    /**
     * The gate that matters most. Cost is null when it was never captured — NOT zero — so letting
     * null pass would put a product of unknown profitability on a discounted page with no way to
     * find out. Same call Wave 18 had to make explicit for the coupon margin guard.
     */
    @Test
    public void anUncostedProductFailsClosed() {
        assertEquals(SeasonCandidateScorer.Rejection.UNCOSTED,
                SeasonCandidateScorer.gate(goods("30.00", true), null, 20,
                        FLOOR, CEILING, null, null),
                "cost unknown must mean ineligible, never 'passes'");
    }

    @Test
    public void aZeroCostIsTreatedAsUncostedNotAsInfiniteMargin() {
        assertEquals(SeasonCandidateScorer.Rejection.UNCOSTED,
                SeasonCandidateScorer.gate(goods("30.00", true), BigDecimal.ZERO, 20,
                        FLOOR, CEILING, null, null));
    }

    @Test
    public void aProductThatCannotSurviveTheSeasonDiscountIsRejected() {
        // 10.00 × 0.85 = 8.50, which is under 9.00 × 1.05.
        assertEquals(SeasonCandidateScorer.Rejection.UNPROFITABLE,
                SeasonCandidateScorer.gate(goods("10.00", true), new BigDecimal("9.00"), 20,
                        FLOOR, CEILING, null, null));
    }

    @Test
    public void offSaleOrOutOfStockIsUnavailable() {
        assertEquals(SeasonCandidateScorer.Rejection.UNAVAILABLE,
                SeasonCandidateScorer.gate(goods("30.00", false), new BigDecimal("12.00"), 20,
                        FLOOR, CEILING, null, null));
        assertEquals(SeasonCandidateScorer.Rejection.UNAVAILABLE,
                SeasonCandidateScorer.gate(goods("30.00", true), new BigDecimal("12.00"), 0,
                        FLOOR, CEILING, null, null));
    }

    /** The store floor is read from config; a product AT the floor stays eligible, as elsewhere. */
    @Test
    public void theStoreFloorMatchesTheStoresOwnComparison() {
        assertEquals(SeasonCandidateScorer.Rejection.NONE,
                SeasonCandidateScorer.gate(goods("5.00", true), new BigDecimal("2.00"), 20,
                        FLOOR, CEILING, null, null),
                "exactly at the floor is not below it");
        assertEquals(SeasonCandidateScorer.Rejection.OUT_OF_BAND,
                SeasonCandidateScorer.gate(goods("4.99", true), new BigDecimal("2.00"), 20,
                        FLOOR, CEILING, null, null));
    }

    @Test
    public void aSeasonMayNarrowThePriceBandButNotWidenTheGates() {
        assertEquals(SeasonCandidateScorer.Rejection.OUT_OF_BAND,
                SeasonCandidateScorer.gate(goods("30.00", true), new BigDecimal("12.00"), 20,
                        FLOOR, CEILING, new BigDecimal("40.00"), null));
    }

    // ---- freshness ---------------------------------------------------------

    /**
     * Freshness is a BONUS, never a decay toward zero. The score is multiplicative, so a factor
     * reaching 0 would zero everything and bury cold-start stock permanently.
     */
    @Test
    public void freshnessNeverFallsBelowOne() {
        assertEquals(1.0, SeasonCandidateScorer.freshnessBonus(9999, 1.25), 0.0001,
                "an old product is un-boosted, never penalised");
        assertEquals(1.25, SeasonCandidateScorer.freshnessBonus(0, 1.25), 0.0001);
        assertTrue(SeasonCandidateScorer.freshnessBonus(15, 1.25) > 1.0);
        assertTrue(SeasonCandidateScorer.freshnessBonus(15, 1.25) < 1.25);
    }

    @Test
    public void aFreshnessCeilingBelowOneCannotShrinkAScore() {
        assertEquals(1.0, SeasonCandidateScorer.freshnessBonus(0, 0.0), 0.0001);
    }

    // ---- score and tier ----------------------------------------------------

    @Test
    public void euStockAndCategoryMatchRaiseTheScoreWithoutExcludingAnyone() {
        SeasonWeights weights = SeasonWeights.DEFAULTS;
        BigDecimal plain = SeasonCandidateScorer.score(new BigDecimal("60"), 20,
                new BigDecimal("4.5"), 10, false, false, 5, weights);
        BigDecimal boosted = SeasonCandidateScorer.score(new BigDecimal("60"), 20,
                new BigDecimal("4.5"), 10, true, true, 5, weights);
        assertTrue(boosted.compareTo(plain) > 0, "EU stock is a boost");
        assertTrue(plain.signum() > 0, "and never a filter — the un-boosted product still scores");
    }

    @Test
    public void tiersFollowTheDealScorerBands() {
        assertEquals(LitemallSeasonCandidate.TIER_HOT,
                SeasonCandidateScorer.tierOf(new BigDecimal("120")));
        assertEquals(LitemallSeasonCandidate.TIER_FEATURED,
                SeasonCandidateScorer.tierOf(new BigDecimal("70")));
        assertEquals(LitemallSeasonCandidate.TIER_WATCH,
                SeasonCandidateScorer.tierOf(new BigDecimal("10")));
    }

    @Test
    public void theAutoPublishBarIsInclusiveUpwardAndFailsClosedOnNonsense() {
        assertTrue(SeasonCandidateScorer.tierReaches("hot", "featured"));
        assertTrue(SeasonCandidateScorer.tierReaches("featured", "featured"));
        assertFalse(SeasonCandidateScorer.tierReaches("watch", "featured"));
        assertFalse(SeasonCandidateScorer.tierReaches("featured", "typo"),
                "an unrecognised bar must not publish everything");
        assertFalse(SeasonCandidateScorer.tierReaches(null, "watch"));
    }
}
