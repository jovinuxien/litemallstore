package org.linlinjava.litemall.goods.application.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallSearchStatMapper;
import org.linlinjava.litemall.db.domain.LitemallKeyword;
import org.linlinjava.litemall.db.service.LitemallKeywordService;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSearchStatsProperties;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Trending-refresh behavior: demand promotes matching curated rows to hot WITHOUT ever
 * deleting or unhotting them; new demand queries are added flagged auto (sort_order 999
 * sentinel — the table's origin marker); only stale AUTO rows leave the hot set; quality
 * filters (min searches, predominantly-zero-result) and the kill-switch hold.
 */
public class SearchTrendingServiceTest {

    private LitemallSearchStatMapper statMapper;
    private LitemallKeywordService keywordService;
    private LitemallSearchStatsProperties properties;
    private SearchTrendingService service;

    @BeforeEach
    public void setUp() {
        statMapper = mock(LitemallSearchStatMapper.class);
        keywordService = mock(LitemallKeywordService.class);
        properties = new LitemallSearchStatsProperties();
        service = new SearchTrendingService(statMapper, keywordService, properties);
        when(statMapper.selectRange(any(), any(), anyInt())).thenReturn(List.of());
        when(keywordService.querySelective(isNull(), isNull(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(new ArrayList<>());
    }

    private static Map<String, Object> demand(String keyword, long searches, long zeroResults) {
        Map<String, Object> row = new HashMap<>();
        row.put("keyword", keyword);
        row.put("searches", searches);
        row.put("zeroResults", zeroResults);
        return row;
    }

    private static LitemallKeyword keywordRow(int id, String keyword, boolean hot, int sortOrder) {
        LitemallKeyword row = new LitemallKeyword();
        row.setId(id);
        row.setKeyword(keyword);
        row.setIsHot(hot);
        row.setIsDefault(false);
        row.setSortOrder(sortOrder);
        return row;
    }

    @Test
    public void killSwitchLeavesKeywordsUntouched() {
        properties.setEnabled(false);
        Map<String, Object> summary = service.refresh();
        assertEquals(false, summary.get("enabled"));
        verify(keywordService, never()).add(any());
        verify(keywordService, never()).updateById(any());
    }

    @Test
    public void matchingCuratedRowIsMarkedHotAndNeverDeleted() {
        when(statMapper.selectRange(any(), any(), anyInt()))
                .thenReturn(List.of(demand("Dresses", 10, 0)));
        when(keywordService.querySelective(isNull(), isNull(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(List.of(keywordRow(7, " Dresses ", false, 100)));

        Map<String, Object> summary = service.refresh();

        ArgumentCaptor<LitemallKeyword> captor = ArgumentCaptor.forClass(LitemallKeyword.class);
        verify(keywordService).updateById(captor.capture());
        assertEquals(7, captor.getValue().getId());
        assertEquals(true, captor.getValue().getIsHot());
        // Curated identity untouched: only is_hot rides the selective update.
        assertEquals(null, captor.getValue().getKeyword());
        assertEquals(null, captor.getValue().getSortOrder());
        verify(keywordService, never()).add(any());
        verify(keywordService, never()).deleteById(any());
        assertEquals(1, summary.get("markedHot"));
        assertEquals(0, summary.get("added"));
    }

    @Test
    public void newDemandQueryIsAddedWithAutoSortOrderMarker() {
        when(statMapper.selectRange(any(), any(), anyInt()))
                .thenReturn(List.of(demand("linen skirt", 6, 1)));

        Map<String, Object> summary = service.refresh();

        ArgumentCaptor<LitemallKeyword> captor = ArgumentCaptor.forClass(LitemallKeyword.class);
        verify(keywordService).add(captor.capture());
        LitemallKeyword added = captor.getValue();
        assertEquals("linen skirt", added.getKeyword());
        assertEquals(true, added.getIsHot());
        assertEquals(false, added.getIsDefault());
        assertEquals(SearchTrendingService.AUTO_SORT_ORDER, added.getSortOrder());
        assertEquals("", added.getUrl());
        assertEquals(1, summary.get("added"));
    }

    @Test
    public void staleAutoRowIsUnhottedButCuratedHotRowsNeverAre() {
        when(statMapper.selectRange(any(), any(), anyInt()))
                .thenReturn(List.of(demand("dresses", 10, 0)));
        LitemallKeyword curatedHot = keywordRow(1, "summer sale", true, 100);
        LitemallKeyword staleAuto = keywordRow(2, "old fad", true, SearchTrendingService.AUTO_SORT_ORDER);
        LitemallKeyword matchingAuto = keywordRow(3, "dresses", true, SearchTrendingService.AUTO_SORT_ORDER);
        when(keywordService.querySelective(isNull(), isNull(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(List.of(curatedHot, staleAuto, matchingAuto));

        Map<String, Object> summary = service.refresh();

        ArgumentCaptor<LitemallKeyword> captor = ArgumentCaptor.forClass(LitemallKeyword.class);
        verify(keywordService, times(1)).updateById(captor.capture());
        assertEquals(2, captor.getValue().getId());
        assertEquals(false, captor.getValue().getIsHot());
        verify(keywordService, never()).deleteById(any());
        assertEquals(1, summary.get("unhotted"));
        assertEquals(1, summary.get("alreadyHot"));
    }

    @Test
    public void qualityFiltersDropWeakAndZeroResultDemand() {
        properties.setTrendingMinSearches(3);
        when(statMapper.selectRange(any(), any(), anyInt())).thenReturn(List.of(
                demand("rare thing", 2, 0),        // under min searches
                demand("ghost product", 10, 5),    // >= half zero-result — would dead-end customers
                demand("good query", 5, 1)));

        Map<String, Object> summary = service.refresh();

        ArgumentCaptor<LitemallKeyword> captor = ArgumentCaptor.forClass(LitemallKeyword.class);
        verify(keywordService, times(1)).add(captor.capture());
        assertEquals("good query", captor.getValue().getKeyword());
        @SuppressWarnings("unchecked")
        List<String> top = (List<String>) summary.get("top");
        assertEquals(List.of("good query"), top);
    }

    @Test
    public void topNCapHolds() {
        properties.setTrendingTop(2);
        properties.setTrendingMinSearches(1);
        when(statMapper.selectRange(any(), any(), anyInt())).thenReturn(List.of(
                demand("a", 9, 0), demand("b", 8, 0), demand("c", 7, 0)));

        service.refresh();

        ArgumentCaptor<LitemallKeyword> captor = ArgumentCaptor.forClass(LitemallKeyword.class);
        verify(keywordService, times(2)).add(captor.capture());
        assertTrue(captor.getAllValues().stream().map(LitemallKeyword::getKeyword).toList()
                .containsAll(List.of("a", "b")));
    }
}
