package org.linlinjava.litemall.goods.application.deals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallDealCandidateMapper;
import org.linlinjava.litemall.db.domain.LitemallDealCandidate;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.db.domain.LitemallSeckill;
import org.linlinjava.litemall.db.service.LitemallGoodsProductService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallDealsProperties;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Auto-deal hard rules: kill-switch stops everything, only {@code proposed hot} candidates are
 * read (dismissed rows untouched by construction), price floors at cost × 1.05, cost-unknown
 * and author-path refusals are skipped WITHOUT consuming the cap, quota = min(stock, cap).
 */
public class AutoDailyDealTaskTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 29);
    private static final LocalDateTime NOW = TODAY.atTime(5, 0);

    private LitemallDealCandidateMapper candidateMapper;
    private FlashDealService flashDealService;
    private LitemallGoodsService goodsService;
    private LitemallGoodsProductService productService;
    private LitemallDealsProperties properties;
    private AutoDailyDealTask task;

    @BeforeEach
    public void setUp() {
        candidateMapper = mock(LitemallDealCandidateMapper.class);
        flashDealService = mock(FlashDealService.class);
        goodsService = mock(LitemallGoodsService.class);
        productService = mock(LitemallGoodsProductService.class);
        properties = new LitemallDealsProperties();
        task = new AutoDailyDealTask(candidateMapper, flashDealService, goodsService,
                productService, properties);
        when(candidateMapper.selectByDay(any(), any())).thenReturn(List.of());
        when(candidateMapper.updateStatus(anyInt(), any(), any())).thenReturn(1);
        LitemallSeckill deal = new LitemallSeckill();
        deal.setId(99);
        when(flashDealService.create(any(), any(), any(), any(), any()))
                .thenReturn(FlashDealService.Result.ok(deal));
    }

    private LitemallDealCandidate hot(int id, int goodsId, String score, String suggested) {
        LitemallDealCandidate c = new LitemallDealCandidate();
        c.setId(id);
        c.setGoodsId(goodsId);
        c.setDay(TODAY);
        c.setTier("hot");
        c.setScore(new BigDecimal(score));
        c.setSuggestedDealPrice(suggested == null ? null : new BigDecimal(suggested));
        c.setStatus(LitemallDealCandidate.STATUS_PROPOSED);
        return c;
    }

    private void goodsWith(int id, String cost, String retail, int stock) {
        LitemallGoods g = new LitemallGoods();
        g.setId(id);
        g.setIsOnSale(true);
        g.setCost(cost == null ? null : new BigDecimal(cost));
        g.setRetailPrice(retail == null ? null : new BigDecimal(retail));
        when(goodsService.findById(id)).thenReturn(g);
        LitemallGoodsProduct sku = new LitemallGoodsProduct();
        sku.setNumber(stock);
        when(productService.queryByGid(id)).thenReturn(List.of(sku));
    }

    @Test
    public void killSwitchCreatesNothingAndReadsNothing() {
        properties.setAutoDailyEnabled(false);
        Map<String, Object> summary = task.run(TODAY, NOW);
        assertEquals(0, summary.get("created"));
        verify(candidateMapper, never()).selectByDay(any(), any());
        verify(flashDealService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    public void onlyProposedRowsAreEverRead() {
        task.run(TODAY, NOW);
        verify(candidateMapper).selectByDay(TODAY, LitemallDealCandidate.STATUS_PROPOSED);
        verify(candidateMapper).selectByDay(TODAY.minusDays(1), LitemallDealCandidate.STATUS_PROPOSED);
        verify(candidateMapper, never()).selectByDay(any(), eq(LitemallDealCandidate.STATUS_DISMISSED));
    }

    @Test
    public void priceFloorsAtCostTimes105() {
        // suggested 8.00 would sell below cost 10.00 → floor 10.50 wins; retail 20 keeps it a markdown
        when(candidateMapper.selectByDay(eq(TODAY), any()))
                .thenReturn(List.of(hot(1, 42, "120", "8.00")));
        goodsWith(42, "10.00", "20.00", 100);

        Map<String, Object> summary = task.run(TODAY, NOW);

        assertEquals(1, summary.get("created"));
        verify(flashDealService).create(eq(42), eq(new BigDecimal("10.50")), eq(NOW),
                eq(NOW.plusHours(24)), eq(50)); // quota = min(100 stock, 50 cap)
        verify(candidateMapper).updateStatus(1, LitemallDealCandidate.STATUS_PROPOSED,
                LitemallDealCandidate.STATUS_APPROVED);
    }

    @Test
    public void floorAtOrAboveRetailIsNoHonestMarkdownAndSkips() {
        // cost 10 → floor 10.50 >= retail 10.40 — nothing honest to offer
        when(candidateMapper.selectByDay(eq(TODAY), any()))
                .thenReturn(List.of(hot(1, 42, "120", null)));
        goodsWith(42, "10.00", "10.40", 100);

        Map<String, Object> summary = task.run(TODAY, NOW);

        assertEquals(0, summary.get("created"));
        verify(flashDealService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    public void costUnknownSkipsWithoutConsumingTheCap() {
        properties.setAutoDailyCap(1);
        when(candidateMapper.selectByDay(eq(TODAY), any())).thenReturn(List.of(
                hot(1, 41, "150", "12.00"), // higher score but no captured cost
                hot(2, 42, "120", "12.00")));
        goodsWith(41, null, "20.00", 100);
        goodsWith(42, "10.00", "20.00", 100);

        Map<String, Object> summary = task.run(TODAY, NOW);

        assertEquals(1, summary.get("created"));
        verify(flashDealService).create(eq(42), any(), any(), any(), any());
        verify(flashDealService, never()).create(eq(41), any(), any(), any(), any());
    }

    @Test
    public void authorPathRefusalSkipsWithoutConsumingTheCap() {
        properties.setAutoDailyCap(1);
        when(candidateMapper.selectByDay(eq(TODAY), any())).thenReturn(List.of(
                hot(1, 41, "150", "12.00"), hot(2, 42, "120", "12.00")));
        goodsWith(41, "10.00", "20.00", 100);
        goodsWith(42, "10.00", "20.00", 100);
        when(flashDealService.create(eq(41), any(), any(), any(), any()))
                .thenReturn(FlashDealService.Result.fail(651, "overlap"));

        Map<String, Object> summary = task.run(TODAY, NOW);

        assertEquals(1, summary.get("created"));
        verify(flashDealService).create(eq(42), any(), any(), any(), any());
    }

    @Test
    public void capBoundsCreationAcrossTodayAndYesterdayRankedByScore() {
        properties.setAutoDailyCap(2);
        List<LitemallDealCandidate> today = new ArrayList<>();
        today.add(hot(1, 41, "110", "12.00"));
        when(candidateMapper.selectByDay(eq(TODAY), any())).thenReturn(today);
        when(candidateMapper.selectByDay(eq(TODAY.minusDays(1)), any())).thenReturn(List.of(
                hot(2, 42, "150", "12.00"), hot(3, 43, "90", "12.00")));
        goodsWith(41, "10.00", "20.00", 100);
        goodsWith(42, "10.00", "20.00", 100);
        goodsWith(43, "10.00", "20.00", 100);

        Map<String, Object> summary = task.run(TODAY, NOW);

        assertEquals(2, summary.get("created"));
        // best scores win: 150 (goods 42) and 110 (goods 41); 90 never reached
        verify(flashDealService).create(eq(42), any(), any(), any(), any());
        verify(flashDealService).create(eq(41), any(), any(), any(), any());
        verify(flashDealService, never()).create(eq(43), any(), any(), any(), any());
        verify(flashDealService, times(2)).create(any(), any(), any(), any(), any());
    }

    @Test
    public void nonHotTiersAreIgnored() {
        LitemallDealCandidate watch = hot(1, 42, "60", "12.00");
        watch.setTier("watch");
        when(candidateMapper.selectByDay(eq(TODAY), any())).thenReturn(List.of(watch));
        goodsWith(42, "10.00", "20.00", 100);

        Map<String, Object> summary = task.run(TODAY, NOW);

        assertEquals(0, summary.get("created"));
        verify(flashDealService, never()).create(any(), any(), any(), any(), any());
    }
}
