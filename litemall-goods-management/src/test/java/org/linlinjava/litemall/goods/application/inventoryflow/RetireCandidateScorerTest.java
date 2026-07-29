package org.linlinjava.litemall.goods.application.inventoryflow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.db.dao.LitemallProductMetricDailyMapper;
import org.linlinjava.litemall.db.dao.LitemallRetireCandidateMapper;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallProductMetricDaily;
import org.linlinjava.litemall.db.domain.LitemallRetireCandidate;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.infrastructure.configuration.InventoryFlowProperties;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Retirement scorer gates: streaks are "days since the last available=1 row" (metric gaps are
 * not evidence), lanes only propose HARD availability trips, admin decisions and the dismiss
 * cooldown are respected, and the composite score/reasons stay honest.
 */
public class RetireCandidateScorerTest {

    private static final int GOODS = 42;
    private static final String PID = "pid-1";

    private LitemallRetireCandidateMapper retireMapper;
    private LitemallProductMetricDailyMapper metricMapper;
    private LitemallCjLinkageMapper linkageMapper;
    private LitemallGoodsService goodsService;
    private RetireCandidateScorer scorer;

    @BeforeEach
    public void setUp() {
        retireMapper = mock(LitemallRetireCandidateMapper.class);
        metricMapper = mock(LitemallProductMetricDailyMapper.class);
        linkageMapper = mock(LitemallCjLinkageMapper.class);
        goodsService = mock(LitemallGoodsService.class);
        scorer = new RetireCandidateScorer(retireMapper, metricMapper, linkageMapper, goodsService,
                new LitemallGoodsProperties(), new InventoryFlowProperties());
        when(linkageMapper.findAnyGoodsIdByCjPid(PID)).thenReturn(GOODS);
    }

    private static LitemallGoods goods(boolean onSale) {
        LitemallGoods g = new LitemallGoods();
        g.setId(GOODS);
        g.setIsOnSale(onSale);
        g.setRetailPrice(new BigDecimal("12.50"));
        g.setCost(new BigDecimal("10.00"));
        g.setAddTime(LocalDateTime.now().minusDays(60));
        return g;
    }

    private static LitemallProductMetricDaily row(int daysAgo, boolean available) {
        LitemallProductMetricDaily m = new LitemallProductMetricDaily();
        m.setGoodsId(GOODS);
        m.setDay(LocalDate.now().minusDays(daysAgo));
        m.setAvailable(available);
        m.setViews(0);
        m.setSalesQty(0);
        return m;
    }

    // ---- streak semantics -----------------------------------------------------------------------

    @Test
    public void emptySeriesIsNoEvidenceAndNoStreak() {
        assertEquals(0, RetireCandidateScorer.unavailableStreakDays(List.of(), goods(true), LocalDate.now()));
    }

    @Test
    public void latestRowAvailableMeansNoStreak() {
        List<LitemallProductMetricDaily> series = List.of(row(5, false), row(1, true));
        assertEquals(0, RetireCandidateScorer.unavailableStreakDays(series, goods(true), LocalDate.now()));
    }

    @Test
    public void streakCountsDaysSinceLastAvailableAcrossGaps() {
        // available 5 days ago, one unavailable row 2 days ago, nights 3-4 missed entirely
        List<LitemallProductMetricDaily> series = List.of(row(5, true), row(2, false));
        assertEquals(5, RetireCandidateScorer.unavailableStreakDays(series, goods(true), LocalDate.now()));
    }

    @Test
    public void neverAvailableAnchorsOnGoodsArrival() {
        LitemallGoods g = goods(true);
        g.setAddTime(LocalDateTime.now().minusDays(10));
        List<LitemallProductMetricDaily> series = List.of(row(2, false));
        assertEquals(10, RetireCandidateScorer.unavailableStreakDays(series, g, LocalDate.now()));
    }

    // ---- lane gates ------------------------------------------------------------------------------

    @Test
    public void unresolvedPidProposesNothing() {
        when(linkageMapper.findAnyGoodsIdByCjPid("pid-x")).thenReturn(null);
        scorer.observe(new ProductFlowEvent(ProductFlowEvent.Kind.VANISHED, "pid-x"));
        verify(retireMapper, never()).upsertProposal(any());
    }

    @Test
    public void offSaleGoodsProposesNothing() {
        when(goodsService.findById(GOODS)).thenReturn(goods(false));
        scorer.observe(new ProductFlowEvent(ProductFlowEvent.Kind.VANISHED, PID));
        verify(retireMapper, never()).upsertProposal(any());
    }

    @Test
    public void streakBelowThresholdProposesNothing() {
        when(goodsService.findById(GOODS)).thenReturn(goods(true));
        // default threshold 3; streak = 2
        when(metricMapper.selectSeries(anyInt(), anyInt()))
                .thenReturn(List.of(row(2, true), row(1, false)));
        scorer.observe(new ProductFlowEvent(ProductFlowEvent.Kind.VANISHED, PID));
        verify(retireMapper, never()).upsertProposal(any());
    }

    @Test
    public void streakAtThresholdProposesWithReason() {
        when(goodsService.findById(GOODS)).thenReturn(goods(true));
        when(metricMapper.selectSeries(anyInt(), anyInt()))
                .thenReturn(List.of(row(4, true), row(1, false)));
        scorer.observe(new ProductFlowEvent(ProductFlowEvent.Kind.VANISHED, PID));

        ArgumentCaptor<LitemallRetireCandidate> captor =
                ArgumentCaptor.forClass(LitemallRetireCandidate.class);
        verify(retireMapper).upsertProposal(captor.capture());
        LitemallRetireCandidate c = captor.getValue();
        assertEquals(Integer.valueOf(GOODS), c.getGoodsId());
        assertEquals(LitemallRetireCandidate.STATUS_PROPOSED, c.getStatus());
        assertTrue(c.getReasons().contains("unavailable at CJ for 4 days"), c.getReasons());
        // streak 4×10 + zero engagement 25 (+ margin fine at 20%) = 65
        assertEquals(new BigDecimal("65.00"), c.getScore());
    }

    // ---- decision guards -------------------------------------------------------------------------

    private void trippedStreak() {
        when(goodsService.findById(GOODS)).thenReturn(goods(true));
        when(metricMapper.selectSeries(anyInt(), anyInt()))
                .thenReturn(List.of(row(6, true), row(1, false)));
    }

    private static LitemallRetireCandidate existing(String status, int daysAgo) {
        LitemallRetireCandidate c = new LitemallRetireCandidate();
        c.setId(7);
        c.setGoodsId(GOODS);
        c.setDay(LocalDate.now().minusDays(daysAgo));
        c.setStatus(status);
        return c;
    }

    @Test
    public void pendingApprovalIsNeverReProposed() {
        trippedStreak();
        when(retireMapper.selectLatestByGoods(GOODS))
                .thenReturn(existing(LitemallRetireCandidate.STATUS_APPROVED, 2));
        scorer.observe(new ProductFlowEvent(ProductFlowEvent.Kind.VANISHED, PID));
        verify(retireMapper, never()).upsertProposal(any());
    }

    @Test
    public void freshDismissalCoolsDownReProposals() {
        trippedStreak();
        when(retireMapper.selectLatestByGoods(GOODS))
                .thenReturn(existing(LitemallRetireCandidate.STATUS_DISMISSED, 2));
        scorer.observe(new ProductFlowEvent(ProductFlowEvent.Kind.VANISHED, PID));
        verify(retireMapper, never()).upsertProposal(any());
    }

    @Test
    public void oldDismissalNoLongerBlocks() {
        trippedStreak();
        // default cooldown 14 days
        when(retireMapper.selectLatestByGoods(GOODS))
                .thenReturn(existing(LitemallRetireCandidate.STATUS_DISMISSED, 20));
        scorer.observe(new ProductFlowEvent(ProductFlowEvent.Kind.VANISHED, PID));
        verify(retireMapper).upsertProposal(any());
    }

    // ---- governor entry --------------------------------------------------------------------------

    @Test
    public void proposeWeakKeepsCallerReasonsAndReportsSuccess() {
        when(goodsService.findById(GOODS)).thenReturn(goods(true));
        when(metricMapper.selectSeries(anyInt(), anyInt())).thenReturn(List.of());

        assertTrue(scorer.proposeWeak(GOODS, List.of("catalog governance: 12500 on-sale vs target 12000")));

        ArgumentCaptor<LitemallRetireCandidate> captor =
                ArgumentCaptor.forClass(LitemallRetireCandidate.class);
        verify(retireMapper).upsertProposal(captor.capture());
        assertTrue(captor.getValue().getReasons().contains("catalog governance"),
                captor.getValue().getReasons());
    }

    @Test
    public void proposeWeakOnOffSaleGoodsReportsFalse() {
        when(goodsService.findById(GOODS)).thenReturn(goods(false));
        assertEquals(false, scorer.proposeWeak(GOODS, List.of("x")));
        verify(retireMapper, never()).upsertProposal(any());
    }
}
