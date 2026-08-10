package org.linlinjava.litemall.goods.application.search;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.application.seo.MetaCatalogFeedService;
import org.linlinjava.litemall.goods.application.seo.SitemapService;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Wave-25 catalog hygiene: unit glyph normalization + Chinese-name rename/off-sale semantics. */
public class CatalogHygieneServiceTest {

    private LitemallGoodsService goodsService;
    private LitemallCjProductService cjProductStore;
    private SearchReindexService reindexService;
    private SitemapService sitemapService;
    private MetaCatalogFeedService feedService;
    private CatalogHygieneService service;

    @BeforeEach
    public void setUp() {
        goodsService = mock(LitemallGoodsService.class);
        cjProductStore = mock(LitemallCjProductService.class);
        reindexService = mock(SearchReindexService.class);
        sitemapService = mock(SitemapService.class);
        feedService = mock(MetaCatalogFeedService.class);
        service = new CatalogHygieneService(goodsService, cjProductStore,
                reindexService, sitemapService, feedService);
    }

    private void stubGoodsPage(List<LitemallGoods> all) {
        when(goodsService.querySelective(eq((Integer) null), eq((String) null), eq((String) null),
                anyInt(), anyInt(), eq("id"), eq("asc")))
                .thenAnswer(inv -> (int) inv.getArgument(3) == 1 ? all : Collections.emptyList());
    }

    private static LitemallGoods goods(int id, String name, String unit, boolean onSale, String cjPid) {
        LitemallGoods g = new LitemallGoods();
        g.setId(id);
        g.setName(name);
        g.setUnit(unit);
        g.setIsOnSale(onSale);
        g.setCjPid(cjPid);
        return g;
    }

    @Test
    public void cjkUnitIsBlankedAndRowReindexed() {
        stubGoodsPage(List.of(goods(1, "Clean Name", "件", true, null)));

        Map<String, Object> result = service.run();

        assertEquals(1, result.get("unitsNormalized"));
        assertEquals(0, result.get("renamed"));
        ArgumentCaptor<LitemallGoods> captor = ArgumentCaptor.forClass(LitemallGoods.class);
        verify(goodsService).updateById(captor.capture());
        assertEquals("", captor.getValue().getUnit());
        assertNull(captor.getValue().getName()); // selective patch — name untouched
        verify(reindexService).reindexGoods(1);
    }

    @Test
    public void chineseNamedGoodsRenamesFromCleanSnapshotTitle() {
        stubGoodsPage(List.of(goods(2, "全自动雨伞", "unit(s)", true, "pid-2")));
        LitemallCjProduct snapshot = new LitemallCjProduct();
        snapshot.setTitle("Automatic Folding Umbrella");
        when(cjProductStore.findByPid("pid-2")).thenReturn(snapshot);

        Map<String, Object> result = service.run();

        assertEquals(1, result.get("renamed"));
        assertEquals(0, result.get("offSaled"));
        ArgumentCaptor<LitemallGoods> captor = ArgumentCaptor.forClass(LitemallGoods.class);
        verify(goodsService).updateById(captor.capture());
        assertEquals("Automatic Folding Umbrella", captor.getValue().getName());
        assertEquals("Automatic Folding Umbrella", captor.getValue().getKeywords());
        assertNull(captor.getValue().getIsOnSale()); // stays on sale
        verify(sitemapService).rebuild(); // slugs derive from names
        verify(feedService).rebuild();
    }

    @Test
    public void chineseNamedGoodsWithoutCleanTitleIsOffSaled() {
        stubGoodsPage(List.of(goods(3, "全自动雨伞", null, true, "pid-3")));
        LitemallCjProduct snapshot = new LitemallCjProduct();
        snapshot.setTitle("雨伞"); // snapshot title is Chinese too — unusable
        when(cjProductStore.findByPid("pid-3")).thenReturn(snapshot);

        Map<String, Object> result = service.run();

        assertEquals(0, result.get("renamed"));
        assertEquals(1, result.get("offSaled"));
        ArgumentCaptor<LitemallGoods> captor = ArgumentCaptor.forClass(LitemallGoods.class);
        verify(goodsService).updateById(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getIsOnSale());
        assertNull(captor.getValue().getName()); // name kept for the reversible retirement
        verify(reindexService).reindexGoods(3); // off-sale flip drops the OCS document
    }

    @Test
    public void cleanCatalogIsANoOp() {
        stubGoodsPage(List.of(
                goods(4, "Clean Name", "pc", true, null),
                goods(5, "已下架中文名", null, false, null))); // off-sale rows keep their name

        Map<String, Object> result = service.run();

        assertEquals(2, result.get("scanned"));
        assertEquals(0, result.get("unitsNormalized"));
        assertEquals(0, result.get("renamed"));
        assertEquals(0, result.get("offSaled"));
        verify(goodsService, never()).updateById(any());
        verify(reindexService, never()).reindexGoods(anyInt());
        verify(sitemapService, never()).rebuild();
    }

    @Test
    public void hanDetectionIsPrecise() {
        assertTrue(CatalogHygieneService.containsHan("件"));
        assertTrue(CatalogHygieneService.containsHan("Mixed 中文 name"));
        assertFalse(CatalogHygieneService.containsHan("unit(s)"));
        assertFalse(CatalogHygieneService.containsHan("Café niño — ünïts"));
        assertFalse(CatalogHygieneService.containsHan(null));
    }
}
