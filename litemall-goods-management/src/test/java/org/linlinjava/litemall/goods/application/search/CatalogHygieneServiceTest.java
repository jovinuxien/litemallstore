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

    // ---------------- supplier prefix (2026-08-26) ----------------

    @Test
    public void supplierPrefixIsStrippedFromTheTitle() {
        // Verbatim from the live feed, fullwidth colon and all.
        stubGoodsPage(List.of(goods(6,
                "Support Pan European：Battery Stapler 3.6V Electric Stapler Nailer",
                "pc", true, null)));

        Map<String, Object> result = service.run();

        assertEquals(1, result.get("prefixesStripped"));
        assertEquals(0, result.get("offSaled"));
        ArgumentCaptor<LitemallGoods> captor = ArgumentCaptor.forClass(LitemallGoods.class);
        verify(goodsService).updateById(captor.capture());
        assertEquals("Battery Stapler 3.6V Electric Stapler Nailer", captor.getValue().getName());
        // keywords keeps the original text so search recall is unchanged.
        assertNull(captor.getValue().getKeywords());
        verify(sitemapService).rebuild(); // the slugged link derives from the name
        verify(feedService).rebuild();
    }

    @Test
    public void supplierPrefixAcceptsAsciiColonAndOddSpacing() {
        assertEquals("Water Pressure Reducer",
                CatalogHygieneService.stripSupplierPrefix("Support Pan European: Water Pressure Reducer"));
        assertEquals("Water Pressure Reducer",
                CatalogHygieneService.stripSupplierPrefix("  support  pan  european：Water Pressure Reducer"));
        assertEquals("Water Pressure Reducer",
                CatalogHygieneService.stripSupplierPrefix("Support Pan European Water Pressure Reducer"));
        // Untouched when the note is absent, and null-safe.
        assertEquals("Ordinary Title", CatalogHygieneService.stripSupplierPrefix("Ordinary Title"));
        assertNull(CatalogHygieneService.stripSupplierPrefix(null));
    }

    @Test
    public void aTitleThatIsOnlyThePrefixIsNotEmptied() {
        // Stripping to "" would put a blank name in front of customers; keep the original and
        // let the readability check route it to rename-or-retire instead.
        assertEquals("Support Pan European：",
                CatalogHygieneService.stripSupplierPrefix("Support Pan European："));
    }

    @Test
    public void prefixIsStrippedBeforeJudgingLanguage() {
        // The whole point of the ordering: this is a good English title wearing a supplier note.
        // Judging language first would see "Support Pan European：Battery Hand Circular Saw ...
        // Für ..." and off-sale a product that only needed a trim.
        stubGoodsPage(List.of(goods(7,
                "Support Pan European：Battery Hand Circular Saw 125mm For 18V Makita", "pc", true, null)));

        Map<String, Object> result = service.run();

        assertEquals(1, result.get("prefixesStripped"));
        assertEquals(0, result.get("offSaled"));
        assertEquals(0, result.get("renamed"));
    }

    // ---------------- non-English titles (2026-08-26) ----------------

    @Test
    public void spanishTitleIsOffSaledWhenNoCleanSnapshotExists() {
        stubGoodsPage(List.of(goods(8,
                "Ducha Portátil Recargable Para Camping, Sin Instalación", "pc", true, null)));

        Map<String, Object> result = service.run();

        assertEquals(1, result.get("offSaled"));
        ArgumentCaptor<LitemallGoods> captor = ArgumentCaptor.forClass(LitemallGoods.class);
        verify(goodsService).updateById(captor.capture());
        assertFalse(captor.getValue().getIsOnSale());
    }

    @Test
    public void germanTitleRenamesFromAnEnglishSnapshot() {
        stubGoodsPage(List.of(goods(9,
                "Kerzenhalter Kegel Metall Für Kerzenständer Und Kandelaber", "pc", true, "pid-9")));
        LitemallCjProduct snapshot = new LitemallCjProduct();
        snapshot.setTitle("Metal Cone Candle Holder For Candlesticks");
        when(cjProductStore.findByPid("pid-9")).thenReturn(snapshot);

        Map<String, Object> result = service.run();

        assertEquals(1, result.get("renamed"));
        assertEquals(0, result.get("offSaled"));
    }

    @Test
    public void aSnapshotInTheSameForeignLanguageIsNotAcceptedAsARename() {
        // Otherwise the rename path "fixes" a Spanish title by writing the same Spanish back.
        stubGoodsPage(List.of(goods(10,
                "Máquina Para Hacer Helados Comercial Con Motor", "pc", true, "pid-10")));
        LitemallCjProduct snapshot = new LitemallCjProduct();
        snapshot.setTitle("Máquina Para Hacer Helados Comercial, Con Bomba De Agua");
        when(cjProductStore.findByPid("pid-10")).thenReturn(snapshot);

        Map<String, Object> result = service.run();

        assertEquals(0, result.get("renamed"));
        assertEquals(1, result.get("offSaled"));
    }

    @Test
    public void readabilityCheckDoesNotFireOnEnglishTitles() {
        // Every one of these is live English copy that a looser rule flagged while I was tuning
        // it. "Accessory Y Retractable Cable" is why single-letter tokens are excluded, and
        // "Macramé" is why one accent alone is not enough.
        assertTrue(CatalogHygieneService.usableTitle(
                "8-Piece Boho Flower Pot Decoration Set, 2x 1.22m Macramé Hanging"));
        assertTrue(CatalogHygieneService.usableTitle(
                "Central Control Charging Adapter Accessory Y Retractable Cable"));
        assertTrue(CatalogHygieneService.usableTitle(
                "Outdoor Hanging Wind Chimes, Decorative Metal Wind Chime With A Gentle Sound"));
        assertTrue(CatalogHygieneService.usableTitle("Solar Power PIR Motion Sensor Wall Lights"));

        assertFalse(CatalogHygieneService.usableTitle(
                "Pastillas Efervescentes Para Limpiar Inodoro | Desodorante De Larga Duración"));
        assertFalse(CatalogHygieneService.usableTitle(
                "Elektrische Küchenmaschine Mit 7 Geschwindigkeitsstufen Und Zubehör"));
        assertFalse(CatalogHygieneService.usableTitle("全自动雨伞"));
        assertFalse(CatalogHygieneService.usableTitle(null));
        assertFalse(CatalogHygieneService.usableTitle("   "));
    }

    @Test
    public void oneForeignWordAloneIsNotEnoughWithoutAnAccent() {
        // "Der" as a model/brand token in otherwise ASCII English must not trip the check.
        assertTrue(CatalogHygieneService.usableTitle("Der Tool Storage Rack Wall Mounted"));
        // ...but one foreign word PLUS a non-ASCII letter is a real signal.
        assertFalse(CatalogHygieneService.usableTitle("Gartenstecker Für Rosé"));
    }

    @Test
    public void offSaleRowsAreLeftAloneRegardlessOfLanguage() {
        stubGoodsPage(List.of(goods(11, "Ducha Portátil Para Camping Sin Instalación", "pc", false, null)));

        Map<String, Object> result = service.run();

        assertEquals(0, result.get("offSaled"));
        assertEquals(0, result.get("renamed"));
        verify(goodsService, never()).updateById(any());
    }
}
