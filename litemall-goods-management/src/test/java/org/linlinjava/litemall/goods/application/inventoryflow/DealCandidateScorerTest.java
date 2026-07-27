package org.linlinjava.litemall.goods.application.inventoryflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallDealCandidateMapper;
import org.linlinjava.litemall.db.domain.LitemallDealCandidate;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Scorer honesty + tiering: no proposal without a captured cost / positive margin /
 * sellable stock; tiers ride the documented thresholds (≥100 hot, ≥70 featured, else
 * watch); the suggested deal price never goes below cost × 1.05.
 */
public class DealCandidateScorerTest {

    private LitemallDealCandidateMapper candidateMapper;
    private DealCandidateScorer scorer;

    @BeforeEach
    public void setUp() {
        candidateMapper = mock(LitemallDealCandidateMapper.class);
        scorer = new DealCandidateScorer(candidateMapper, new LitemallGoodsProperties());
    }

    private static ProductInventoryContext ctx(BigDecimal retail, BigDecimal cost,
                                               BigDecimal marginPct, int stock,
                                               BigDecimal rating, Integer reviews) {
        return new ProductInventoryContext(
                new ProductFlowEvent(ProductFlowEvent.Kind.NEW_ARRIVAL, "pid-1"),
                42, retail, cost, marginPct, stock, rating, reviews);
    }

    @Test
    public void noCostMeansNoProposal() {
        scorer.score(ctx(new BigDecimal("12.50"), null, null, 100, null, null));
        verify(candidateMapper, never()).upsertProposal(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void lowStockMeansNoProposal() {
        // default stock-low-threshold is 5
        scorer.score(ctx(new BigDecimal("12.50"), new BigDecimal("10.00"),
                new BigDecimal("20.00"), 5, null, null));
        verify(candidateMapper, never()).upsertProposal(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void unresolvedGoodsMeansNoProposal() {
        scorer.score(ProductInventoryContext.unresolved(
                new ProductFlowEvent(ProductFlowEvent.Kind.NEW_ARRIVAL, "pid-x")));
        verify(candidateMapper, never()).upsertProposal(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void healthyArrivalWithSocialProofScoresHot() {
        // margin 20%, stock 200, rating 5.0, 200 reviews → 20 × ln(201) × 1.5 ≈ 159 → hot
        scorer.score(ctx(new BigDecimal("12.50"), new BigDecimal("10.00"),
                new BigDecimal("20.00"), 200, new BigDecimal("5.0"), 200));

        ArgumentCaptor<LitemallDealCandidate> captor = ArgumentCaptor.forClass(LitemallDealCandidate.class);
        verify(candidateMapper).upsertProposal(captor.capture());
        LitemallDealCandidate c = captor.getValue();
        assertEquals("hot", c.getTier());
        assertTrue(c.getScore().compareTo(new BigDecimal("100")) >= 0, "score " + c.getScore());
        assertEquals(LitemallDealCandidate.STATUS_PROPOSED, c.getStatus());
        assertEquals(Integer.valueOf(42), c.getGoodsId());
        assertTrue(c.getReasons().contains("margin"), c.getReasons());
    }

    @Test
    public void modestArrivalWithoutSocialProofScoresLowerTier() {
        // margin 20%, stock 10, no reviews → 20 × ln(11) ≈ 48 → watch
        scorer.score(ctx(new BigDecimal("12.50"), new BigDecimal("10.00"),
                new BigDecimal("20.00"), 10, null, null));

        ArgumentCaptor<LitemallDealCandidate> captor = ArgumentCaptor.forClass(LitemallDealCandidate.class);
        verify(candidateMapper).upsertProposal(captor.capture());
        assertEquals("watch", captor.getValue().getTier());
    }

    @Test
    public void suggestedPriceNeverGoesBelowCostFloor() {
        // 15% off retail (10.62) would undercut cost 10.00 × 1.05 = 10.50 → floor wins
        scorer.score(ctx(new BigDecimal("12.00"), new BigDecimal("10.00"),
                new BigDecimal("16.67"), 50, null, null));

        ArgumentCaptor<LitemallDealCandidate> captor = ArgumentCaptor.forClass(LitemallDealCandidate.class);
        verify(candidateMapper).upsertProposal(captor.capture());
        assertEquals(new BigDecimal("10.50"), captor.getValue().getSuggestedDealPrice());
    }
}
