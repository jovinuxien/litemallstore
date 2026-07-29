package org.linlinjava.litemall.goods.application.inventoryflow;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallRetireCandidateMapper;
import org.linlinjava.litemall.db.dao.LitemallSeckillMapper;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallRetireCandidate;
import org.linlinjava.litemall.db.domain.LitemallSeckill;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.application.search.SearchReindexService;
import org.linlinjava.litemall.goods.application.seo.SitemapService;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Executor semantics: only due approved rows act (the mapper owns the execute_on <= today
 * filter), the off-sale flip is a NARROW selective update + per-goods reindex, live-deal goods
 * are skipped and stay approved, and the sitemap/cache refresh only after real work.
 */
public class RetirementExecutorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 29);

    private LitemallRetireCandidateMapper retireMapper;
    private LitemallGoodsService goodsService;
    private LitemallSeckillMapper seckillMapper;
    private SearchReindexService reindexService;
    private SitemapService sitemapService;
    private CategoryInsightCache insightCache;
    private RetirementExecutor executor;

    @BeforeEach
    public void setUp() {
        retireMapper = mock(LitemallRetireCandidateMapper.class);
        goodsService = mock(LitemallGoodsService.class);
        seckillMapper = mock(LitemallSeckillMapper.class);
        reindexService = mock(SearchReindexService.class);
        sitemapService = mock(SitemapService.class);
        insightCache = mock(CategoryInsightCache.class);
        executor = new RetirementExecutor(retireMapper, goodsService, seckillMapper,
                reindexService, sitemapService, insightCache);
        when(retireMapper.updateStatus(anyInt(), any(), any(), any())).thenReturn(1);
    }

    private static LitemallRetireCandidate due(int id, int goodsId) {
        LitemallRetireCandidate c = new LitemallRetireCandidate();
        c.setId(id);
        c.setGoodsId(goodsId);
        c.setDay(TODAY.minusDays(3));
        c.setStatus(LitemallRetireCandidate.STATUS_APPROVED);
        c.setExecuteOn(TODAY);
        return c;
    }

    private static LitemallGoods onSaleGoods(int id) {
        LitemallGoods g = new LitemallGoods();
        g.setId(id);
        g.setIsOnSale(true);
        return g;
    }

    @Test
    public void dueApprovedGoodsIsFlippedOffSaleReindexedAndMarkedExecuted() {
        when(retireMapper.selectDueApproved(TODAY)).thenReturn(List.of(due(7, 42)));
        when(goodsService.findById(42)).thenReturn(onSaleGoods(42));

        Map<String, Object> summary = executor.execute(TODAY);

        ArgumentCaptor<LitemallGoods> captor = ArgumentCaptor.forClass(LitemallGoods.class);
        verify(goodsService).updateById(captor.capture());
        LitemallGoods update = captor.getValue();
        assertEquals(Integer.valueOf(42), update.getId());
        assertFalse(update.getIsOnSale());
        // NARROW payload — never a full-aggregate write that would clobber prices
        assertEquals(null, update.getRetailPrice());
        assertEquals(null, update.getName());
        verify(reindexService).reindexGoods(42);
        verify(retireMapper).updateStatus(7, LitemallRetireCandidate.STATUS_APPROVED,
                LitemallRetireCandidate.STATUS_EXECUTED, null);
        verify(sitemapService).rebuild();
        verify(insightCache).refresh(true);
        assertEquals(1, summary.get("executed"));
    }

    @Test
    public void liveDealGoodsIsSkippedAndStaysApproved() {
        when(retireMapper.selectDueApproved(TODAY)).thenReturn(List.of(due(7, 42)));
        when(seckillMapper.selectLiveByGoodsId(42)).thenReturn(new LitemallSeckill());

        Map<String, Object> summary = executor.execute(TODAY);

        verify(goodsService, never()).updateById(any());
        verify(retireMapper, never()).updateStatus(anyInt(), any(), any(), any());
        verify(sitemapService, never()).rebuild();
        assertEquals(1, summary.get("skippedLiveDeal"));
        assertEquals(0, summary.get("executed"));
    }

    @Test
    public void missingGoodsIsMarkedExecutedWithoutAFlip() {
        when(retireMapper.selectDueApproved(TODAY)).thenReturn(List.of(due(7, 42)));
        when(goodsService.findById(42)).thenReturn(null);

        Map<String, Object> summary = executor.execute(TODAY);

        verify(goodsService, never()).updateById(any());
        verify(retireMapper).updateStatus(eq(7), any(), eq(LitemallRetireCandidate.STATUS_EXECUTED), any());
        assertEquals(1, summary.get("goodsMissing"));
        assertEquals(1, summary.get("executed"));
    }

    @Test
    public void lostCasRaceIsCountedNotExecuted() {
        when(retireMapper.selectDueApproved(TODAY)).thenReturn(List.of(due(7, 42)));
        when(goodsService.findById(42)).thenReturn(onSaleGoods(42));
        when(retireMapper.updateStatus(anyInt(), any(), any(), any())).thenReturn(0);

        Map<String, Object> summary = executor.execute(TODAY);

        assertEquals(0, summary.get("executed"));
        assertEquals(1, summary.get("lostRace"));
    }

    @Test
    public void nothingDueMeansNoSitemapOrCacheChurn() {
        when(retireMapper.selectDueApproved(TODAY)).thenReturn(List.of());

        Map<String, Object> summary = executor.execute(TODAY);

        verify(sitemapService, never()).rebuild();
        verify(insightCache, never()).refresh(true);
        assertEquals(0, summary.get("due"));
    }
}
