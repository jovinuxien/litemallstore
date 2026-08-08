package org.linlinjava.litemall.goods.interfaces.rest;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallSearchHistory;
import org.linlinjava.litemall.goods.application.search.CategorySearchService;
import org.linlinjava.litemall.goods.application.search.SearchHistoryService;
import org.linlinjava.litemall.goods.application.search.SearchKeywordService;
import org.linlinjava.litemall.goods.application.search.SearchService;
import org.linlinjava.litemall.goods.utils.UserContext;
import org.springframework.web.client.RestClientException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave-22 threading of the hit total through GET /srv/search: the intent is recorded BEFORE
 * the OCS round-trip (outage must not skip the history write), and the total is stamped back
 * best-effort AFTER — an OCS failure leaves result_count NULL, an anonymous search records
 * nothing at all.
 */
public class LitemallSearchControllerResultCountTest {

    private SearchService searchService;
    private SearchHistoryService searchHistoryService;
    private LitemallSearchController controller;

    @BeforeEach
    public void setUp() {
        searchService = mock(SearchService.class);
        searchHistoryService = mock(SearchHistoryService.class);
        controller = new LitemallSearchController(searchService,
                mock(SearchKeywordService.class), mock(CategorySearchService.class),
                searchHistoryService);
    }

    @AfterEach
    public void clearIdentity() {
        UserContext.setUserId(null);
    }

    private Object search(String query) {
        return controller.search(query, 1, 20, null, new HashMap<>());
    }

    @Test
    public void loggedInSearchStampsTheTotalOntoItsHistoryRow() {
        UserContext.setUserId("42");
        LitemallSearchHistory row = new LitemallSearchHistory();
        row.setId(5);
        when(searchHistoryService.record(eq(42), anyString())).thenReturn(row);
        Map<String, Object> result = new HashMap<>();
        result.put("total", 37L);
        when(searchService.search(anyString(), anyInt(), anyInt(), isNull(), anyMap())).thenReturn(result);

        assertNotNull(search("dress"));

        verify(searchHistoryService).record(42, "dress");
        verify(searchHistoryService).recordResultCount(row, 37L);
    }

    @Test
    public void ocsOutageStillRecordsIntentButLeavesCountUnknown() {
        UserContext.setUserId("42");
        LitemallSearchHistory row = new LitemallSearchHistory();
        row.setId(6);
        when(searchHistoryService.record(eq(42), anyString())).thenReturn(row);
        when(searchService.search(anyString(), anyInt(), anyInt(), isNull(), anyMap()))
                .thenThrow(new RestClientException("down"));

        assertNotNull(search("dress"));

        verify(searchHistoryService).record(42, "dress");
        verify(searchHistoryService, never()).recordResultCount(any(), anyLong());
    }

    @Test
    public void anonymousSearchRecordsNothing() {
        when(searchService.search(anyString(), anyInt(), anyInt(), isNull(), anyMap()))
                .thenReturn(new HashMap<>(Map.of("total", 3L)));

        assertNotNull(search("dress"));

        verify(searchHistoryService, never()).record(any(), any());
        verify(searchHistoryService, never()).recordResultCount(any(), anyLong());
    }
}
