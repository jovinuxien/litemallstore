package org.linlinjava.litemall.goods.application.search;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallSearchStatMapper;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave-22 contract shape of GET /srv/private/admin/insight/search-stats: topQueries with a
 * 1-dp ctrPct (null — never 0 — when there are no searches), zeroResultQueries, totals, and
 * the [1, 90] window clamp. Aggregate-only: rows carry keyword + counts, nothing else.
 */
public class SearchStatAdminServiceTest {

    private LitemallSearchStatMapper statMapper;
    private SearchStatAdminService service;

    @BeforeEach
    public void setUp() {
        statMapper = mock(LitemallSearchStatMapper.class);
        service = new SearchStatAdminService(statMapper);
        when(statMapper.selectRange(any(), any(), anyInt())).thenReturn(List.of());
        when(statMapper.selectRangeZeroTop(any(), any(), anyInt())).thenReturn(List.of());
        when(statMapper.selectRangeTotals(any(), any())).thenReturn(null);
    }

    private static Map<String, Object> row(String keyword, long searches, long zeroResults, long clicks) {
        Map<String, Object> map = new HashMap<>();
        map.put("keyword", keyword);
        map.put("searches", searches);
        map.put("zeroResults", zeroResults);
        map.put("clicks", clicks);
        return map;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shapesTopZeroAndTotalsWithCtr() {
        when(statMapper.selectRange(any(), any(), eq(50)))
                .thenReturn(List.of(row("dress", 40, 2, 13), row("noclicks", 5, 0, 0)));
        when(statMapper.selectRangeZeroTop(any(), any(), eq(50)))
                .thenReturn(List.of(row("ghost", 7, 7, 0)));
        Map<String, Object> totals = new HashMap<>();
        totals.put("searches", 45L);
        totals.put("zeroResults", 9L);
        totals.put("clicks", 13L);
        when(statMapper.selectRangeTotals(any(), any())).thenReturn(totals);

        Map<String, Object> stats = service.stats(7);

        List<Map<String, Object>> topQueries = (List<Map<String, Object>>) stats.get("topQueries");
        assertEquals(2, topQueries.size());
        assertEquals("dress", topQueries.get(0).get("keyword"));
        assertEquals(40L, topQueries.get(0).get("searches"));
        assertEquals(2L, topQueries.get(0).get("zeroResults"));
        assertEquals(13L, topQueries.get(0).get("clicks"));
        assertEquals(new BigDecimal("32.5"), topQueries.get(0).get("ctrPct"));
        assertEquals(new BigDecimal("0.0"), topQueries.get(1).get("ctrPct"));

        List<Map<String, Object>> zeroQueries = (List<Map<String, Object>>) stats.get("zeroResultQueries");
        assertEquals(1, zeroQueries.size());
        assertEquals("ghost", zeroQueries.get(0).get("keyword"));
        assertEquals(7L, zeroQueries.get(0).get("zeroResults"));

        Map<String, Object> shapedTotals = (Map<String, Object>) stats.get("totals");
        assertEquals(45L, shapedTotals.get("searches"));
        assertEquals(9L, shapedTotals.get("zeroResults"));
        assertEquals(13L, shapedTotals.get("clicks"));
        assertEquals(7, stats.get("days"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void emptyWindowYieldsZeroTotalsAndNullCtr() {
        Map<String, Object> stats = service.stats(7);
        assertEquals(List.of(), stats.get("topQueries"));
        assertEquals(List.of(), stats.get("zeroResultQueries"));
        Map<String, Object> totals = (Map<String, Object>) stats.get("totals");
        assertEquals(0L, totals.get("searches"));
        assertNull(totals.get("ctrPct"));
    }

    @Test
    public void windowClampsToOneThroughNinety() {
        service.stats(0);
        service.stats(500);
        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        verify(statMapper, org.mockito.Mockito.times(2)).selectRange(from.capture(), any(), anyInt());
        assertEquals(LocalDate.now(), from.getAllValues().get(0));                 // days=0 -> 1
        assertEquals(LocalDate.now().minusDays(89), from.getAllValues().get(1));   // days=500 -> 90
    }
}
