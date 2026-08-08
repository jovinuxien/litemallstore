package org.linlinjava.litemall.goods.application.search;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallSearchHistory;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave-22 result_count capture at the history seam: record() returns the row the search
 * belongs to (fresh insert OR the consecutive-duplicate row, so a repeat search refreshes
 * its hit total); recordResultCount() stamps result_count by id, null-safe on every input.
 */
public class SearchHistoryResultCountTest {

    private org.linlinjava.litemall.db.service.LitemallSearchHistoryService dbService;
    private SearchHistoryService service;

    @BeforeEach
    public void setUp() {
        dbService = mock(org.linlinjava.litemall.db.service.LitemallSearchHistoryService.class);
        service = new SearchHistoryService(dbService);
        when(dbService.querySelective(anyString(), isNull(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(List.of());
    }

    @Test
    public void recordReturnsTheFreshRow() {
        LitemallSearchHistory row = service.record(42, "  dress  ");
        verify(dbService).save(any());
        assertEquals("dress", row.getKeyword());
        assertEquals(42, row.getUserId());
        assertNull(row.getResultCount());
    }

    @Test
    public void consecutiveDuplicateReturnsExistingRowWithoutSaving() {
        LitemallSearchHistory existing = new LitemallSearchHistory();
        existing.setId(9);
        existing.setKeyword("dress");
        when(dbService.querySelective(anyString(), isNull(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(List.of(existing));
        LitemallSearchHistory row = service.record(42, "dress");
        verify(dbService, never()).save(any());
        assertSame(existing, row);
    }

    @Test
    public void anonymousOrBlankRecordsNothing() {
        assertNull(service.record(null, "dress"));
        assertNull(service.record(42, "   "));
        verify(dbService, never()).save(any());
    }

    @Test
    public void recordResultCountPatchesById() {
        LitemallSearchHistory row = new LitemallSearchHistory();
        row.setId(11);
        service.recordResultCount(row, 37L);
        ArgumentCaptor<LitemallSearchHistory> captor = ArgumentCaptor.forClass(LitemallSearchHistory.class);
        verify(dbService).updateById(captor.capture());
        assertEquals(11, captor.getValue().getId());
        assertEquals(37, captor.getValue().getResultCount());
        // Selective patch: nothing else rides along.
        assertNull(captor.getValue().getKeyword());
        assertNull(captor.getValue().getUserId());
    }

    @Test
    public void zeroTotalIsARealZeroResultSearch() {
        LitemallSearchHistory row = new LitemallSearchHistory();
        row.setId(12);
        service.recordResultCount(row, 0L);
        ArgumentCaptor<LitemallSearchHistory> captor = ArgumentCaptor.forClass(LitemallSearchHistory.class);
        verify(dbService).updateById(captor.capture());
        assertEquals(0, captor.getValue().getResultCount());
    }

    @Test
    public void nullSafeOnMissingRowIdOrTotal() {
        service.recordResultCount(null, 5L);
        LitemallSearchHistory noId = new LitemallSearchHistory();
        service.recordResultCount(noId, 5L);
        LitemallSearchHistory row = new LitemallSearchHistory();
        row.setId(13);
        service.recordResultCount(row, null);
        service.recordResultCount(row, -1L);
        verify(dbService, never()).updateById(any());
    }
}
