package org.linlinjava.litemall.goods.application.promo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.dao.LitemallPromoCandidateMapper;
import org.linlinjava.litemall.db.domain.LitemallPromoCandidate;
import org.linlinjava.litemall.goods.application.insight.MarginBasisService;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallPromoCandidateProperties;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Nightly-task behavior: kill-switch means zero writes; the per-kind cap holds; a proposed
 * retirement marks its goods' coupon proposal as clearance; a hot arrival root upgrades
 * exactly ONE row (its best) to a category-scoped suggestion; the summary is honest.
 */
public class PromoCandidateNightlyTaskTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 8);

    private InsightMapper insightMapper;
    private LitemallPromoCandidateMapper candidateMapper;
    private MarginBasisService marginBasisService;
    private CategoryRootResolver rootResolver;
    private LitemallPromoCandidateProperties properties;
    private PromoCandidateNightlyTask task;

    @BeforeEach
    public void setUp() {
        insightMapper = mock(InsightMapper.class);
        candidateMapper = mock(LitemallPromoCandidateMapper.class);
        marginBasisService = mock(MarginBasisService.class);
        rootResolver = mock(CategoryRootResolver.class);
        properties = new LitemallPromoCandidateProperties();
        LitemallGoodsProperties goodsProperties = new LitemallGoodsProperties();
        task = new PromoCandidateNightlyTask(insightMapper, candidateMapper,
                new CouponCandidateScorer(properties, goodsProperties),
                new GrouponCandidateScorer(properties, goodsProperties),
                marginBasisService, rootResolver, properties);
        when(insightMapper.selectRetireCandidateRows(anyString(), anyInt())).thenReturn(List.of());
        when(rootResolver.rootOf(any())).thenReturn(null);
    }

    private static Map<String, Object> poolRow(int goodsId, int categoryId, String retail,
                                               String cost, LocalDateTime arrival) {
        Map<String, Object> row = new HashMap<>();
        row.put("goodsId", goodsId);
        row.put("categoryId", categoryId);
        row.put("retailPrice", new BigDecimal(retail));
        row.put("cost", new BigDecimal(cost));
        row.put("marginPct", new BigDecimal(retail).subtract(new BigDecimal(cost))
                .divide(new BigDecimal(retail), 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")));
        row.put("stockTotal", 100);
        row.put("salesQty", 0);
        row.put("views", 10);
        row.put("arrivalDate", arrival);
        return row;
    }

    @Test
    public void killSwitchMeansZeroWrites() {
        properties.setEnabled(false);
        Map<String, Object> summary = task.run(DAY);
        assertEquals(false, summary.get("enabled"));
        verify(candidateMapper, never()).upsertProposal(any());
        verify(insightMapper, never()).selectPromoScoringPool(anyInt());
    }

    @Test
    public void perKindCapHolds() {
        properties.setMaxPerKind(3);
        List<Map<String, Object>> pool = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            pool.add(poolRow(i, 1000 + i, "25.00", "10.00", null));
        }
        when(insightMapper.selectPromoScoringPool(anyInt())).thenReturn(pool);

        Map<String, Object> summary = task.run(DAY);

        assertEquals(3, summary.get("coupon"));
        assertEquals(3, summary.get("groupon"));
        ArgumentCaptor<LitemallPromoCandidate> captor =
                ArgumentCaptor.forClass(LitemallPromoCandidate.class);
        verify(candidateMapper, org.mockito.Mockito.times(6)).upsertProposal(captor.capture());
        long coupons = captor.getAllValues().stream()
                .filter(c -> LitemallPromoCandidate.KIND_COUPON.equals(c.getKind())).count();
        assertEquals(3, coupons);
    }

    @Test
    public void proposedRetirementBecomesClearance() {
        when(insightMapper.selectPromoScoringPool(anyInt()))
                .thenReturn(List.of(poolRow(42, 1008, "25.00", "10.00", null)));
        Map<String, Object> retireRow = new HashMap<>();
        retireRow.put("goodsId", 42);
        when(insightMapper.selectRetireCandidateRows(anyString(), anyInt()))
                .thenReturn(List.of(retireRow));

        task.run(DAY);

        ArgumentCaptor<LitemallPromoCandidate> captor =
                ArgumentCaptor.forClass(LitemallPromoCandidate.class);
        verify(candidateMapper, org.mockito.Mockito.atLeastOnce()).upsertProposal(captor.capture());
        LitemallPromoCandidate coupon = captor.getAllValues().stream()
                .filter(c -> LitemallPromoCandidate.KIND_COUPON.equals(c.getKind()))
                .findFirst().orElseThrow();
        assertTrue(PromoJson.parseArray(coupon.getReasons()).contains("clearance: retirement proposed"));
        // clearance target 20 fits inside the guard max (58) for cost 10 / retail 25
        assertEquals(20, PromoJson.parseObject(coupon.getSuggestion()).get("discount"));
    }

    @Test
    public void hotArrivalRootUpgradesItsBestRowToCategoryScope() {
        properties.setCategoryArrivalMin(2);
        LocalDateTime fresh = DAY.minusDays(1).atStartOfDay();
        List<Map<String, Object>> pool = List.of(
                poolRow(1, 111, "25.00", "10.00", fresh),   // margin 60%
                poolRow(2, 112, "25.00", "15.00", fresh));  // margin 40%
        when(insightMapper.selectPromoScoringPool(anyInt())).thenReturn(pool);
        when(rootResolver.rootOf(111)).thenReturn(9000);
        when(rootResolver.rootOf(112)).thenReturn(9000);
        when(marginBasisService.basis(any(), anyList()))
                .thenReturn(Map.of("maxCostRatio", new BigDecimal("0.6000")));

        Map<String, Object> summary = task.run(DAY);

        assertEquals(1, summary.get("hotArrivalRoots"));
        ArgumentCaptor<LitemallPromoCandidate> captor =
                ArgumentCaptor.forClass(LitemallPromoCandidate.class);
        verify(candidateMapper, org.mockito.Mockito.atLeastOnce()).upsertProposal(captor.capture());
        List<LitemallPromoCandidate> coupons = captor.getAllValues().stream()
                .filter(c -> LitemallPromoCandidate.KIND_COUPON.equals(c.getKind())).toList();
        assertEquals(2, coupons.size());
        long categoryScoped = coupons.stream()
                .filter(c -> "category".equals(PromoJson.parseObject(c.getSuggestion()).get("scopeType")))
                .count();
        assertEquals(1, categoryScoped);
        LitemallPromoCandidate upgraded = coupons.stream()
                .filter(c -> "category".equals(PromoJson.parseObject(c.getSuggestion()).get("scopeType")))
                .findFirst().orElseThrow();
        assertEquals(1, upgraded.getGoodsId()); // the best-margin row carries the category suggestion
        assertEquals(9000, PromoJson.parseObject(upgraded.getSuggestion()).get("categoryId"));
    }
}
