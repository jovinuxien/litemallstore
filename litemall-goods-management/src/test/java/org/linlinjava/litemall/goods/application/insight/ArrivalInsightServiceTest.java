package org.linlinjava.litemall.goods.application.insight;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.dao.LitemallCjSyncRunMapper;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallCjSyncRun;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.goods.application.goods.CatalogGoodsCountService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Arrivals windows: boundaries come from COMPLETE flow runs only, {@code since} is the oldest
 * run's start (ISO string), zero-arrival categories are omitted, ranking is dealScore desc.
 */
public class ArrivalInsightServiceTest {

    private LitemallCjSyncRunMapper syncRunMapper;
    private InsightMapper insightMapper;
    private LitemallCategoryService categoryService;
    private CatalogGoodsCountService countService;
    private ArrivalInsightService service;

    @BeforeEach
    public void setUp() {
        syncRunMapper = mock(LitemallCjSyncRunMapper.class);
        insightMapper = mock(InsightMapper.class);
        categoryService = mock(LitemallCategoryService.class);
        countService = mock(CatalogGoodsCountService.class);
        service = new ArrivalInsightService(syncRunMapper, insightMapper, categoryService, countService);
    }

    private static LitemallCjSyncRun run(LocalDateTime started) {
        LitemallCjSyncRun r = new LitemallCjSyncRun();
        r.setPhase(LitemallCjSyncRun.PHASE_FLOW);
        r.setStartedTime(started);
        r.setComplete(true);
        return r;
    }

    private static LitemallCategory root(int id, String name) {
        LitemallCategory c = new LitemallCategory();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private static Map<String, Object> agg(long arrivals, String dealScore) {
        Map<String, Object> m = new HashMap<>();
        m.put("arrivals", arrivals);
        m.put("avgMarginPct", new BigDecimal("20.00"));
        m.put("avgRetailPrice", new BigDecimal("12.50"));
        m.put("dealScore", new BigDecimal(dealScore));
        return m;
    }

    @Test
    public void noCompleteRunsMeansAnEmptyHonestWindow() {
        when(syncRunMapper.selectRecentComplete(any(), eq(1))).thenReturn(List.of());
        Map<String, Object> data = service.arrivals(1);
        assertNull(data.get("since"));
        assertEquals(0, data.get("runs"));
        assertEquals(List.of(), data.get("categories"));
    }

    @Test
    public void sinceIsTheOldestRunsStartAndOnlyCompleteFlowRunsAreAsked() {
        LocalDateTime newer = LocalDateTime.of(2026, 7, 29, 3, 0);
        LocalDateTime older = LocalDateTime.of(2026, 7, 28, 3, 0);
        when(syncRunMapper.selectRecentComplete(eq(LitemallCjSyncRun.PHASE_FLOW), eq(2)))
                .thenReturn(List.of(run(newer), run(older)));
        when(categoryService.queryL1()).thenReturn(List.of());

        Map<String, Object> data = service.arrivals(2);

        assertEquals(older.toString(), data.get("since"));
        assertEquals(2, data.get("runs"));
    }

    @Test
    public void zeroArrivalRootsAreOmittedAndRankingIsDealScoreDesc() {
        LocalDateTime started = LocalDateTime.of(2026, 7, 29, 3, 0);
        when(syncRunMapper.selectRecentComplete(any(), eq(1))).thenReturn(List.of(run(started)));
        when(categoryService.queryL1()).thenReturn(List.of(
                root(1, "Pets"), root(2, "Toys"), root(3, "Empty")));
        when(countService.subtreeIds(1)).thenReturn(List.of(1));
        when(countService.subtreeIds(2)).thenReturn(List.of(2));
        when(countService.subtreeIds(3)).thenReturn(List.of(3));
        when(insightMapper.selectArrivalsAgg(eq(List.of(1)), any(), any())).thenReturn(agg(5, "40.00"));
        when(insightMapper.selectArrivalsAgg(eq(List.of(2)), any(), any())).thenReturn(agg(9, "90.00"));
        when(insightMapper.selectArrivalsAgg(eq(List.of(3)), any(), any())).thenReturn(agg(0, "0"));

        Map<String, Object> data = service.arrivals(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) data.get("categories");
        assertEquals(2, categories.size());
        assertEquals("Toys", categories.get(0).get("name"));
        assertEquals("Pets", categories.get(1).get("name"));
    }

    @Test
    public void runsParameterIsClampedTo1Or2() {
        when(syncRunMapper.selectRecentComplete(any(), eq(2))).thenReturn(List.of());
        service.arrivals(7); // clamps to 2
        org.mockito.Mockito.verify(syncRunMapper).selectRecentComplete(any(), eq(2));
    }
}
