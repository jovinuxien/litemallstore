package org.linlinjava.litemall.goods.application.promo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
 * Coupon-scorer honesty: no proposal without cost/margin/stock; the suggested rate is
 * ALWAYS inside the Wave-18 guard bound (1 − cost/retail × 1.05) × 100, so a suggestion
 * can never be rejected when the admin creates it; clearance goes deeper but stays
 * bounded; thin margins yield NO suggestion rather than a token one.
 */
public class CouponCandidateScorerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 8);

    private CouponCandidateScorer scorer;

    @BeforeEach
    public void setUp() {
        scorer = new CouponCandidateScorer(
                new LitemallPromoCandidateProperties(), new LitemallGoodsProperties());
    }

    private static PromoScoringRow row(String retail, String cost, String marginPct, int stock,
                                       String rating, int reviews, int sales) {
        return new PromoScoringRow(42, 1008, new BigDecimal(retail), new BigDecimal(cost),
                marginPct == null ? null : new BigDecimal(marginPct), stock, sales, 57,
                rating == null ? null : new BigDecimal(rating), reviews, null);
    }

    @Test
    public void noCostBasisMeansNoProposal() {
        assertNull(scorer.score(new PromoScoringRow(42, 1008, new BigDecimal("25.00"), null,
                null, 100, 0, 0, null, 0, null), DAY, false, null));
    }

    @Test
    public void lowStockMeansNoProposal() {
        // default stock-low-threshold is 5
        assertNull(scorer.score(row("25.00", "5.00", "80.00", 5, null, 0, 0), DAY, false, null));
    }

    @Test
    public void suggestedRateStaysInsideTheGuardBound() {
        // cost 20 / retail 25 → costRatio 0.8 → max = (1 − 0.8×1.05)×100 = 16% → default 10 fits
        LitemallPromoCandidate candidate =
                scorer.score(row("25.00", "20.00", "20.00", 100, null, 0, 0), DAY, false, null);
        assertNotNull(candidate);
        Map<String, Object> suggestion = PromoJson.parseObject(candidate.getSuggestion());
        assertEquals(1, suggestion.get("discountType"));
        assertEquals(10, suggestion.get("discount"));
        assertEquals(16, suggestion.get("maxDiscount"));
        assertEquals("goods", suggestion.get("scopeType"));
        assertEquals(List.of(42), suggestion.get("goodsIds"));
        // cap = one item's worth: 25.00 × 10% = 2.50
        assertEquals(2.50, ((Number) suggestion.get("discountCap")).doubleValue(), 0.001);
    }

    @Test
    public void clearanceGoesDeeperButStaysBounded() {
        // standard 1.25 pricing: cost 20 / retail 25 → guard max 16 < clearance target 20
        LitemallPromoCandidate candidate =
                scorer.score(row("25.00", "20.00", "20.00", 100, null, 0, 0), DAY, true, null);
        assertNotNull(candidate);
        Map<String, Object> suggestion = PromoJson.parseObject(candidate.getSuggestion());
        assertEquals(16, suggestion.get("discount"));
        assertTrue(PromoJson.parseArray(candidate.getReasons())
                .contains("clearance: retirement proposed"));
    }

    @Test
    public void thinMarginYieldsNoSuggestionAtAll() {
        // cost 24 / retail 25 → max = (1 − 0.96×1.05)×100 = −0.8 → floored 0 < minRate 3
        assertNull(scorer.score(row("25.00", "24.00", "4.00", 100, null, 0, 0), DAY, false, null));
    }

    @Test
    public void categoryScopeUsesTheSubtreeBoundAndRootId() {
        // product-level max would be 58 (cost 10/retail 25); the category's worst product caps it at 6
        LitemallPromoCandidate candidate = scorer.score(
                row("25.00", "10.00", "60.00", 100, null, 0, 0), DAY, false,
                new CouponCandidateScorer.CategoryScope(1036012, 34, 6));
        assertNotNull(candidate);
        Map<String, Object> suggestion = PromoJson.parseObject(candidate.getSuggestion());
        assertEquals("category", suggestion.get("scopeType"));
        assertEquals(1036012, suggestion.get("categoryId"));
        assertEquals(6, suggestion.get("discount"));
        assertNull(suggestion.get("discountCap"));
        assertNull(suggestion.get("goodsIds"));
    }

    @Test
    public void slowMoverOutscoresProvenSellerAndTiersFollowThresholds() {
        LitemallPromoCandidate slow = scorer.score(
                row("25.00", "10.00", "60.00", 300, "4.5", 100, 0), DAY, false, null);
        LitemallPromoCandidate seller = scorer.score(
                row("25.00", "10.00", "60.00", 300, "4.5", 100, 50), DAY, false, null);
        assertNotNull(slow);
        assertNotNull(seller);
        assertTrue(slow.getScore().compareTo(seller.getScore()) > 0);
        assertEquals("hot", slow.getTier());
        assertEquals(LitemallPromoCandidate.KIND_COUPON, slow.getKind());
        assertEquals(LitemallPromoCandidate.STATUS_PROPOSED, slow.getStatus());
        assertEquals(DAY, slow.getDay());
    }
}
