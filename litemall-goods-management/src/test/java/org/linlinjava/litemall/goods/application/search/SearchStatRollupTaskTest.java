package org.linlinjava.litemall.goods.application.search;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallSearchStatMapper;
import org.linlinjava.litemall.db.domain.LitemallSearchStatDaily;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSearchStatsProperties;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rollup behavior: kill-switch means zero writes and no trending; history + behavioral
 * sources merge per NORMALIZED keyword with absolute (idempotent) per-day counts;
 * zero-results come from history only, clicks from events only; a clicks-only keyword
 * still lands a row; the nightly pass covers the trailing window then refreshes trending.
 */
public class SearchStatRollupTaskTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 7);

    private LitemallSearchStatMapper statMapper;
    private SearchTrendingService trendingService;
    private LitemallSearchStatsProperties properties;
    private SearchStatRollupTask task;

    @BeforeEach
    public void setUp() {
        statMapper = mock(LitemallSearchStatMapper.class);
        trendingService = mock(SearchTrendingService.class);
        properties = new LitemallSearchStatsProperties();
        task = new SearchStatRollupTask(statMapper, trendingService, properties);
        when(statMapper.selectHistoryAgg(any(), any())).thenReturn(List.of());
        when(statMapper.selectSearchEventAgg(any(), any())).thenReturn(List.of());
        when(statMapper.selectClickEventAgg(any(), any())).thenReturn(List.of());
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    @Test
    public void killSwitchRollsUpNothingAndSkipsTrending() {
        properties.setEnabled(false);
        Map<String, Object> rollup = task.runRollup(DAY);
        Map<String, Object> nightly = task.runNightly();
        assertEquals(false, rollup.get("enabled"));
        assertEquals(false, nightly.get("enabled"));
        verify(statMapper, never()).upsertDay(any());
        verify(trendingService, never()).refresh();
    }

    @Test
    public void historyAndEventSourcesMergePerNormalizedKeyword() {
        // " Dress " from history must merge with "dress" from the event sources (defensive
        // re-normalization on top of the SQL's own trim/lower).
        when(statMapper.selectHistoryAgg(any(), any())).thenReturn(List.of(
                row("keyword", " Dress ", "searches", 5L, "zeroResults", 1L)));
        when(statMapper.selectSearchEventAgg(any(), any())).thenReturn(List.of(
                row("keyword", "dress", "searches", 3L),
                row("keyword", "shoes", "searches", 2L)));
        when(statMapper.selectClickEventAgg(any(), any())).thenReturn(List.of(
                row("keyword", "dress", "clicks", 4L)));

        Map<String, Object> summary = task.runRollup(DAY);

        ArgumentCaptor<LitemallSearchStatDaily> captor = ArgumentCaptor.forClass(LitemallSearchStatDaily.class);
        verify(statMapper, times(2)).upsertDay(captor.capture());
        Map<String, LitemallSearchStatDaily> byKeyword = new HashMap<>();
        for (LitemallSearchStatDaily stat : captor.getAllValues()) {
            assertEquals(DAY, stat.getDay());
            byKeyword.put(stat.getKeyword(), stat);
        }
        LitemallSearchStatDaily dress = byKeyword.get("dress");
        assertEquals(8, dress.getSearches());
        assertEquals(1, dress.getZeroResults());
        assertEquals(4, dress.getClicks());
        LitemallSearchStatDaily shoes = byKeyword.get("shoes");
        assertEquals(2, shoes.getSearches());
        assertEquals(0, shoes.getZeroResults());
        assertEquals(0, shoes.getClicks());
        assertEquals(2, summary.get("keywords"));
        assertEquals(10L, summary.get("searches"));
        assertEquals(1L, summary.get("zeroResults"));
        assertEquals(4L, summary.get("clicks"));
    }

    @Test
    public void clicksOnlyKeywordStillLandsARow() {
        when(statMapper.selectClickEventAgg(any(), any())).thenReturn(List.of(
                row("keyword", "late click", "clicks", 2L)));
        task.runRollup(DAY);
        ArgumentCaptor<LitemallSearchStatDaily> captor = ArgumentCaptor.forClass(LitemallSearchStatDaily.class);
        verify(statMapper).upsertDay(captor.capture());
        assertEquals("late click", captor.getValue().getKeyword());
        assertEquals(0, captor.getValue().getSearches());
        assertEquals(2, captor.getValue().getClicks());
    }

    @Test
    public void rerunProducesIdenticalAbsoluteCounts() {
        when(statMapper.selectHistoryAgg(any(), any())).thenReturn(List.of(
                row("keyword", "dress", "searches", 5L, "zeroResults", 0L)));
        task.runRollup(DAY);
        task.runRollup(DAY);
        ArgumentCaptor<LitemallSearchStatDaily> captor = ArgumentCaptor.forClass(LitemallSearchStatDaily.class);
        verify(statMapper, times(2)).upsertDay(captor.capture());
        // Absolute recompute both times — the upsert overwrite makes the re-run a no-op in the DB.
        for (LitemallSearchStatDaily stat : captor.getAllValues()) {
            assertEquals("dress", stat.getKeyword());
            assertEquals(5, stat.getSearches());
            assertEquals(0, stat.getZeroResults());
        }
    }

    @Test
    public void nightlyRollsTrailingWindowThenRefreshesTrending() {
        properties.setRollupDays(2);
        Map<String, Object> trendingSummary = Map.of("enabled", true);
        when(trendingService.refresh()).thenReturn(trendingSummary);

        Map<String, Object> summary = task.runNightly();

        // Two day windows swept (yesterday + today) across each of the three sources.
        ArgumentCaptor<LocalDateTime> starts = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(statMapper, times(2)).selectHistoryAgg(starts.capture(), any());
        assertEquals(LocalDate.now().minusDays(1).atStartOfDay(), starts.getAllValues().get(0));
        assertEquals(LocalDate.now().atStartOfDay(), starts.getAllValues().get(1));
        verify(trendingService, times(1)).refresh();
        assertEquals(trendingSummary, summary.get("trending"));
        assertEquals(true, summary.get("enabled"));
    }

    @Test
    public void normalizationTrimsLowercasesAndCapsAt127() {
        assertEquals("dress", SearchStatRollupTask.normalizeKeyword("  DrEsS  "));
        assertNull(SearchStatRollupTask.normalizeKeyword("   "));
        assertNull(SearchStatRollupTask.normalizeKeyword(null));
        String longKeyword = "a".repeat(300);
        String normalized = SearchStatRollupTask.normalizeKeyword(longKeyword);
        assertEquals(127, normalized.length());
        assertTrue(longKeyword.startsWith(normalized));
        assertFalse(SearchStatRollupTask.normalizeKeyword("ok").isEmpty());
    }
}
