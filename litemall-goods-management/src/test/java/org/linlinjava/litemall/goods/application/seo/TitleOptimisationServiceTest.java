package org.linlinjava.litemall.goods.application.seo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.application.search.SearchReindexService;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The write side of the SEO title worklist. The scan side is pinned by TitleProposerTest; this
 * pins what happens once an administrator presses Apply — in particular that a reindex failure
 * is REPORTED, never thrown, because by then the name is already in MySQL.
 */
public class TitleOptimisationServiceTest {

    private LitemallGoodsService goodsService;
    private SearchReindexService reindexService;
    private TitleOptimisationService service;

    @BeforeEach
    public void setUp() {
        goodsService = mock(LitemallGoodsService.class);
        reindexService = mock(SearchReindexService.class);
        service = new TitleOptimisationService(goodsService, mock(LitemallCategoryService.class),
                mock(KeywordResearchProvider.class), reindexService);
    }

    private LitemallGoods live(int id, String name) {
        LitemallGoods g = new LitemallGoods();
        g.setId(id);
        g.setName(name);
        g.setIsOnSale(true);
        when(goodsService.findById(id)).thenReturn(g);
        return g;
    }

    @Test
    public void applyWritesTheSubmittedTitleAndReindexes() {
        live(7, "A very long supplier title that Google will cut somewhere around here");

        TitleOptimisationService.Applied applied = service.apply(7, "A very long supplier title");

        assertEquals("A very long supplier title", applied.title());
        assertTrue(applied.changed());
        assertTrue(applied.reindexed());
        assertNull(applied.reindexError());
        ArgumentCaptor<LitemallGoods> patch = ArgumentCaptor.forClass(LitemallGoods.class);
        verify(goodsService).updateById(patch.capture());
        assertEquals(7, patch.getValue().getId());
        assertEquals("A very long supplier title", patch.getValue().getName());
        verify(reindexService).reindexGoods(7);
    }

    @Test
    public void applyStripsMarkupBeforeJudgingOrWriting() {
        live(7, "Old");
        TitleOptimisationService.Applied applied = service.apply(7, "  <b>Garden</b> hose  ");
        assertEquals("Garden hose", applied.title());
    }

    @Test
    public void applyIsANoOpWhenTheTitleAlreadyMatches() {
        live(7, "Garden hose");
        TitleOptimisationService.Applied applied = service.apply(7, "Garden hose");
        assertFalse(applied.changed());
        assertTrue(applied.reindexed());
        verify(goodsService, never()).updateById(any());
        verify(reindexService, never()).reindexGoods(any());
    }

    @Test
    public void applyRefusesBlankOverlongAndUnknownWithoutWriting() {
        live(7, "Old");
        assertThrows(IllegalArgumentException.class, () -> service.apply(7, "   "));
        assertThrows(IllegalArgumentException.class, () -> service.apply(7, "x".repeat(128)));
        assertThrows(IllegalArgumentException.class, () -> service.apply(99, "fine"));
        assertThrows(IllegalArgumentException.class, () -> service.apply(null, "fine"));
        verify(goodsService, never()).updateById(any());
        verify(reindexService, never()).reindexGoods(any());
    }

    @Test
    public void reindexFailureIsReportedNotThrownBecauseMysqlIsAlreadyWritten() {
        live(7, "Old long title");
        doThrow(new IllegalStateException("indexer down")).when(reindexService).reindexGoods(7);

        TitleOptimisationService.Applied applied = service.apply(7, "New");

        assertTrue(applied.changed(), "the name WAS written");
        assertFalse(applied.reindexed());
        assertEquals("indexer down", applied.reindexError());
        verify(goodsService).updateById(any());
    }

    @Test
    public void batchAppliesEveryRowAndReportsRefusalsInPlace() {
        live(1, "First long title here");
        live(2, "Second long title here");
        // 3 does not exist; 4 is fine but its reindex fails.
        live(4, "Fourth long title here");
        doThrow(new RuntimeException("boom")).when(reindexService).reindexGoods(4);

        List<TitleOptimisationService.BatchResult> results = service.applyBatch(List.of(
                new TitleOptimisationService.TitleChange(1, "First"),
                new TitleOptimisationService.TitleChange(2, "   "),
                new TitleOptimisationService.TitleChange(3, "Third"),
                new TitleOptimisationService.TitleChange(4, "Fourth")));

        assertEquals(4, results.size(), "one result per submitted row, in order");
        assertTrue(results.get(0).ok());
        assertEquals("First", results.get(0).title());
        assertTrue(results.get(0).reindexed());

        assertFalse(results.get(1).ok());
        assertEquals("title must not be blank", results.get(1).error());

        assertFalse(results.get(2).ok());
        assertEquals("no such goods: 3", results.get(2).error());

        assertTrue(results.get(3).ok(), "a reindex failure is not a refusal");
        assertTrue(results.get(3).changed());
        assertFalse(results.get(3).reindexed());
        assertEquals("boom", results.get(3).error());

        // Rows 1 and 4 were written; the refused rows never reached MySQL.
        verify(goodsService, times(2)).updateById(any());
    }

    @Test
    public void batchRefusesEmptyAndOversizedWholesaleWithoutWriting() {
        assertThrows(IllegalArgumentException.class, () -> service.applyBatch(List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.applyBatch(null));

        List<TitleOptimisationService.TitleChange> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < TitleOptimisationService.BATCH_LIMIT + 1; i++) {
            tooMany.add(new TitleOptimisationService.TitleChange(i, "t"));
        }
        assertThrows(IllegalArgumentException.class, () -> service.applyBatch(tooMany));
        verify(goodsService, never()).updateById(any());
    }
}
