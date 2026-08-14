package org.linlinjava.litemall.goods.application.inventoryflow;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.dao.LitemallRetireCandidateMapper;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallRetireCandidate;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.application.pricing.CategoryMarginResolver;
import org.linlinjava.litemall.goods.application.search.SearchReindexService;
import org.linlinjava.litemall.goods.application.seo.SitemapService;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave-26 narrowing semantics. The properties that matter are the destructive ones: only
 * non-anchor goods are staged, an existing admin decision is never taken over, a dry run writes
 * nothing, the anchor set cannot be empty or non-L1 (which would empty the storefront), and a
 * restore puts back ONLY what a narrowing run took off sale — never goods off-saled by the price
 * floor, hygiene or scored retirement.
 */
public class CatalogNarrowingServiceTest {

    private static final int ANCHOR_A = 1036143; // Home, Garden & Furniture
    private static final int ANCHOR_B = 1036495; // Home Improvement
    private static final int OTHER = 1036000;    // some non-anchor L1

    private InsightMapper insightMapper;
    private LitemallRetireCandidateMapper retireMapper;
    private LitemallCategoryService categoryService;
    private LitemallGoodsService goodsService;
    private CategoryMarginResolver categoryResolver;
    private SearchReindexService reindexService;
    private SitemapService sitemapService;
    private CategoryInsightCache insightCache;
    private CatalogNarrowingService service;

    @BeforeEach
    public void setUp() {
        insightMapper = mock(InsightMapper.class);
        retireMapper = mock(LitemallRetireCandidateMapper.class);
        categoryService = mock(LitemallCategoryService.class);
        goodsService = mock(LitemallGoodsService.class);
        categoryResolver = mock(CategoryMarginResolver.class);
        reindexService = mock(SearchReindexService.class);
        sitemapService = mock(SitemapService.class);
        insightCache = mock(CategoryInsightCache.class);
        service = new CatalogNarrowingService(insightMapper, retireMapper, categoryService,
                goodsService, categoryResolver, reindexService, sitemapService, insightCache);

        root(ANCHOR_A, "Home, Garden & Furniture");
        root(ANCHOR_B, "Home Improvement");
        root(OTHER, "Women's Clothing");
        when(insightMapper.selectOnSaleCjPage(anyInt(), anyInt())).thenReturn(List.of());
    }

    private void root(int id, String name) {
        LitemallCategory category = new LitemallCategory();
        category.setId(id);
        category.setName(name);
        category.setPid(0);
        category.setDeleted(false);
        when(categoryService.findById(id)).thenReturn(category);
    }

    /** goods id -> leaf category, with the leaf resolving to the given root. */
    private Map<String, Object> onSale(int goodsId, int leafCategoryId, Integer root) {
        when(categoryResolver.rootOfCategory(leafCategoryId)).thenReturn(root);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("goodsId", goodsId);
        row.put("categoryId", leafCategoryId);
        return row;
    }

    private void pages(List<Map<String, Object>> first) {
        // One populated page, then the empty page that ends the keyset walk.
        when(insightMapper.selectOnSaleCjPage(eq(0), anyInt())).thenReturn(first);
    }

    @Test
    public void stagesOnlyNonAnchorGoods() {
        pages(List.of(
                onSale(10, 501, ANCHOR_A),
                onSale(11, 502, OTHER),
                onSale(12, 503, ANCHOR_B),
                onSale(13, 504, OTHER)));

        Map<String, Object> out = service.narrow(List.of(ANCHOR_A, ANCHOR_B), null, false);

        assertEquals(4, out.get("scanned"));
        assertEquals(2, out.get("staged"));
        assertEquals(2, out.get("keep"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LitemallRetireCandidate>> captor = ArgumentCaptor.forClass(List.class);
        verify(retireMapper).insertApprovedBatch(captor.capture());
        List<Integer> stagedIds = new ArrayList<>();
        for (LitemallRetireCandidate c : captor.getValue()) {
            stagedIds.add(c.getGoodsId());
            assertTrue(c.getReasons().contains(CatalogNarrowingService.REASON_MARKER),
                    "reasons must carry the marker restore matches on");
        }
        assertEquals(List.of(11, 13), stagedIds);
    }

    @Test
    public void goodsInAnUnknownCategoryIsNarrowedNotKept() {
        // An orphaned category must not silently mean "stays on sale".
        pages(List.of(onSale(20, 999, null)));

        Map<String, Object> out = service.narrow(List.of(ANCHOR_A), null, false);

        assertEquals(1, out.get("staged"));
    }

    @Test
    public void neverTakesOverAnExistingDecision() {
        pages(List.of(onSale(30, 502, OTHER), onSale(31, 502, OTHER)));
        LitemallRetireCandidate dismissedToday = new LitemallRetireCandidate();
        dismissedToday.setGoodsId(30);
        dismissedToday.setDay(LocalDate.now());
        dismissedToday.setStatus(LitemallRetireCandidate.STATUS_DISMISSED);
        when(retireMapper.selectLatestByGoods(30)).thenReturn(dismissedToday);

        Map<String, Object> out = service.narrow(List.of(ANCHOR_A), null, false);

        assertEquals(1, out.get("staged"));
        assertEquals(1, out.get("alreadyDecided"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LitemallRetireCandidate>> captor = ArgumentCaptor.forClass(List.class);
        verify(retireMapper).insertApprovedBatch(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(31, captor.getValue().get(0).getGoodsId());
    }

    @Test
    public void dryRunCountsButStagesNothing() {
        pages(List.of(onSale(40, 502, OTHER), onSale(41, 501, ANCHOR_A)));

        Map<String, Object> out = service.narrow(List.of(ANCHOR_A), null, true);

        assertEquals(1, out.get("staged"));
        assertEquals(Boolean.TRUE, out.get("dryRun"));
        verify(retireMapper, never()).insertApprovedBatch(any());
    }

    @Test
    public void executeOnDefaultsToTodayAndIsCarriedOntoEveryRow() {
        pages(List.of(onSale(50, 502, OTHER)));

        Map<String, Object> out = service.narrow(List.of(ANCHOR_A), null, false);
        assertEquals(LocalDate.now().toString(), out.get("executeOn"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LitemallRetireCandidate>> captor = ArgumentCaptor.forClass(List.class);
        verify(retireMapper).insertApprovedBatch(captor.capture());
        assertEquals(LocalDate.now(), captor.getValue().get(0).getExecuteOn());
    }

    @Test
    public void refusesAnEmptyOrNonRootAnchorSet() {
        assertThrows(IllegalArgumentException.class, () -> service.narrow(List.of(), null, false));
        assertThrows(IllegalArgumentException.class, () -> service.narrow(null, null, false));

        LitemallCategory leaf = new LitemallCategory();
        leaf.setId(777);
        leaf.setName("Spatulas");
        leaf.setPid(ANCHOR_A); // a leaf, not an L1 root
        leaf.setDeleted(false);
        when(categoryService.findById(777)).thenReturn(leaf);
        assertThrows(IllegalArgumentException.class, () -> service.narrow(List.of(777), null, false));

        assertThrows(IllegalArgumentException.class, () -> service.narrow(List.of(4242), null, false));
        verify(retireMapper, never()).insertApprovedBatch(any());
    }

    @Test
    public void previewSplitsAnchorFromTheRestWithoutWriting() {
        when(categoryResolver.rootOfCategory(501)).thenReturn(ANCHOR_A);
        when(categoryResolver.rootOfCategory(502)).thenReturn(OTHER);
        when(categoryResolver.rootOfCategory(999)).thenReturn(null);
        when(insightMapper.selectOnSaleCjCountsByCategory()).thenReturn(List.of(
                count(501, 1753), count(502, 6586), count(999, 4)));

        Map<String, Object> out = service.preview(List.of(ANCHOR_A));

        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) out.get("totals");
        assertEquals(8343L, totals.get("onSale"));
        assertEquals(1753L, totals.get("keep"));
        assertEquals(6590L, totals.get("wouldOffSale")); // orphans count as non-anchor
        assertEquals(4L, out.get("orphanedCategoryGoods"));
        verify(retireMapper, never()).insertApprovedBatch(any());
        verify(goodsService, never()).updateById(any());
    }

    private Map<String, Object> count(int categoryId, long cnt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("categoryId", categoryId);
        row.put("cnt", cnt);
        return row;
    }

    @Test
    public void restorePutsBackOnlyTheRequestedRootsAndOnlyNarrowedGoods() {
        when(categoryResolver.rootOfCategory(502)).thenReturn(OTHER);
        when(categoryResolver.rootOfCategory(601)).thenReturn(ANCHOR_B);
        when(insightMapper.selectNarrowedOffSale(
                eq(CatalogNarrowingService.REASON_MARKER),
                eq(LitemallRetireCandidate.STATUS_EXECUTED), anyInt()))
                .thenReturn(List.of(narrowed(900, 60, 502), narrowed(901, 61, 601)));
        when(retireMapper.updateStatus(anyInt(), any(), any(), any())).thenReturn(1);

        Map<String, Object> out = service.restore(List.of(OTHER));

        assertEquals(1, out.get("restored"));
        assertEquals(1, out.get("skippedOtherCategory"));

        ArgumentCaptor<LitemallGoods> goods = ArgumentCaptor.forClass(LitemallGoods.class);
        verify(goodsService).updateById(goods.capture());
        assertEquals(60, goods.getValue().getId());
        assertEquals(Boolean.TRUE, goods.getValue().getIsOnSale());
        verify(reindexService).reindexGoods(60);
        verify(reindexService, never()).reindexGoods(61);
        verify(retireMapper).updateStatus(900, LitemallRetireCandidate.STATUS_EXECUTED,
                LitemallRetireCandidate.STATUS_RESTORED, null);
        verify(sitemapService).rebuild();
    }

    @Test
    public void restoreRefusesAnEmptyCategorySet() {
        assertThrows(IllegalArgumentException.class, () -> service.restore(List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.restore(null));
        verify(goodsService, never()).updateById(any());
    }

    @Test
    public void restoreWithNothingToDoTouchesNothing() {
        when(insightMapper.selectNarrowedOffSale(any(), any(), anyInt())).thenReturn(List.of());

        Map<String, Object> out = service.restore(List.of(OTHER));

        assertEquals(0, out.get("restored"));
        assertEquals(Boolean.FALSE, out.get("truncated"));
        verify(goodsService, never()).updateById(any());
        verify(sitemapService, never()).rebuild();
        assertFalse((Boolean) out.get("truncated"));
    }

    private Map<String, Object> narrowed(int candidateId, int goodsId, int categoryId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("candidateId", candidateId);
        row.put("goodsId", goodsId);
        row.put("categoryId", categoryId);
        return row;
    }
}
