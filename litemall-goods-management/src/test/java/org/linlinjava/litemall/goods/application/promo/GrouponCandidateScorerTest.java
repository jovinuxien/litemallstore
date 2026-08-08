package org.linlinjava.litemall.goods.application.promo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallPromoCandidate;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallPromoCandidateProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Groupon-scorer honesty: the suggested group price never goes below cost × 1.05, a
 * floored price that erases the saving kills the proposal, and social proof outweighs
 * raw margin more than in the coupon formula (group-buys spread by sharing).
 */
public class GrouponCandidateScorerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 8);

    private GrouponCandidateScorer scorer;

    @BeforeEach
    public void setUp() {
        scorer = new GrouponCandidateScorer(
                new LitemallPromoCandidateProperties(), new LitemallGoodsProperties());
    }

    private static PromoScoringRow row(String retail, String cost, String marginPct, int stock,
                                       String rating, int reviews) {
        return new PromoScoringRow(42, 1008, new BigDecimal(retail), new BigDecimal(cost),
                marginPct == null ? null : new BigDecimal(marginPct), stock, 0, 0,
                rating == null ? null : new BigDecimal(rating), reviews, null);
    }

    @Test
    public void groupPriceIsRetailTimesDiscountWhenMarginAllows() {
        // cost 10 / retail 25: 25 × 0.80 = 20.00 > 10 × 1.05 — the discount price wins
        LitemallPromoCandidate candidate = scorer.score(row("25.00", "10.00", "60.00", 100, "4.5", 50), DAY);
        assertNotNull(candidate);
        Map<String, Object> suggestion = PromoJson.parseObject(candidate.getSuggestion());
        assertEquals(20.00, ((Number) suggestion.get("combinationPrice")).doubleValue(), 0.001);
        assertEquals(25.00, ((Number) suggestion.get("originalPrice")).doubleValue(), 0.001);
        assertEquals(1, suggestion.get("limitPerUser"));
        assertEquals(7, suggestion.get("windowDays"));
        assertEquals(LitemallPromoCandidate.KIND_GROUPON, candidate.getKind());
    }

    @Test
    public void costFloorWinsOverTheDiscountPrice() {
        // cost 22 / retail 25: floor 23.10 > 25 × 0.80 — but 23.10 > 25 × 0.92 ⇒ no proposal
        assertNull(scorer.score(row("25.00", "22.00", "12.00", 100, "4.5", 50), DAY));
    }

    @Test
    public void borderlineFloorStillProposesWhenSavingIsReal() {
        // cost 21 / retail 25: floor 22.05 ≤ 25 × 0.92 = 23.00 ⇒ propose at the floor
        LitemallPromoCandidate candidate = scorer.score(row("25.00", "21.00", "16.00", 100, null, 0), DAY);
        assertNotNull(candidate);
        Map<String, Object> suggestion = PromoJson.parseObject(candidate.getSuggestion());
        assertEquals(22.05, ((Number) suggestion.get("combinationPrice")).doubleValue(), 0.001);
    }

    @Test
    public void noCostOrLowStockMeansNoProposal() {
        assertNull(scorer.score(new PromoScoringRow(42, 1008, new BigDecimal("25.00"), null,
                null, 100, 0, 0, null, 0, null), DAY));
        assertNull(scorer.score(row("25.00", "10.00", "60.00", 5, "4.5", 50), DAY));
    }

    @Test
    public void socialProofLiftsScoreAndMemberCountFollowsTier() {
        LitemallPromoCandidate proven = scorer.score(row("25.00", "10.00", "60.00", 300, "4.8", 200), DAY);
        LitemallPromoCandidate unproven = scorer.score(row("25.00", "10.00", "60.00", 300, null, 0), DAY);
        assertNotNull(proven);
        assertNotNull(unproven);
        assertTrue(proven.getScore().compareTo(unproven.getScore()) > 0);
        assertEquals("hot", proven.getTier());
        Map<String, Object> hotSuggestion = PromoJson.parseObject(proven.getSuggestion());
        assertEquals(3, hotSuggestion.get("requiredMembers"));
        assertTrue(PromoJson.parseArray(unproven.getReasons()).contains("no social proof yet"));
    }
}
