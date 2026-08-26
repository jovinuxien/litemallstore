package org.linlinjava.litemall.goods.application.seo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.application.content.PageService;
import org.linlinjava.litemall.goods.application.goods.CatalogGoodsCountService;
import org.linlinjava.litemall.goods.infrastructure.configuration.PublicSiteProperties;
import org.mockito.Mockito;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class SitemapServiceTest {

    private LitemallGoodsService goodsService;
    private LitemallCategoryService categoryService;
    private CatalogGoodsCountService goodsCountService;
    private PageService pageService;
    private PublicSiteProperties siteProperties;
    private SitemapService service;

    @BeforeEach
    void setup() {
        goodsService = Mockito.mock(LitemallGoodsService.class);
        categoryService = Mockito.mock(LitemallCategoryService.class);
        goodsCountService = Mockito.mock(CatalogGoodsCountService.class);
        pageService = Mockito.mock(PageService.class);
        siteProperties = new PublicSiteProperties();
        service = new SitemapService(goodsService, categoryService, goodsCountService, pageService,
                siteProperties);
    }

    @Test
    void emitsHomepageNonEmptyCategoriesAndOnSaleProductsOnly() throws Exception {
        stubCatalog();

        byte[] xml = service.sitemap();
        Document doc = parse(xml);

        assertThat(doc.getDocumentElement().getLocalName()).isEqualTo("urlset");
        assertThat(doc.getDocumentElement().getNamespaceURI())
                .isEqualTo("http://www.sitemaps.org/schemas/sitemap/0.9");

        List<String> locs = texts(doc, "loc");
        assertThat(locs).contains(
                "https://trovemo.com/",
                "https://trovemo.com/category/100",
                "https://trovemo.com/product/1-wireless-earbuds",
                // empty slug (CJK-only name) degrades to the bare-id canonical
                "https://trovemo.com/product/3");
        // category 101 has zero on-sale goods; goods 2 is off-sale
        assertThat(locs).noneMatch(loc -> loc.contains("/category/101"));
        assertThat(locs).noneMatch(loc -> loc.contains("/product/2"));
    }

    @Test
    void lastmodIsW3cDateFromUpdateTimeAndOmittedWhenUnknown() throws Exception {
        stubCatalog();

        Document doc = parse(service.sitemap());
        List<String> lastmods = texts(doc, "lastmod");

        assertThat(lastmods).containsExactly("2026-07-28"); // goods 1 only; goods 3 has null update_time
        assertThat(lastmods).allMatch(value -> value.matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    void trailingSlashBaseUrlDoesNotDoubleSlash() throws Exception {
        siteProperties.setPublicBaseUrl("https://trovemo.com/");
        stubCatalog();

        List<String> locs = texts(parse(service.sitemap()), "loc");

        assertThat(locs).contains("https://trovemo.com/", "https://trovemo.com/category/100");
        assertThat(locs).noneMatch(loc -> loc.contains("com//"));
    }

    @Test
    void secondReadServesCachedBytesWithoutAnotherWalk() {
        stubCatalog();

        byte[] first = service.sitemap();
        byte[] second = service.sitemap();

        assertThat(second).isSameAs(first);
        Mockito.verify(goodsService, Mockito.times(1))
                .querySelective(any(), any(), any(), anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    void failedRebuildKeepsServingThePreviousSnapshot() {
        stubCatalog();
        byte[] first = service.sitemap();

        when(goodsService.querySelective(any(), any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenThrow(new RuntimeException("db down"));

        // rebuild() throws to its caller (the nightly hook catches + logs)…
        assertThatThrownBy(service::rebuild).isInstanceOf(RuntimeException.class);
        // …and the serving path still returns the last good bytes.
        assertThat(service.sitemap()).isSameAs(first);
    }

    @Test
    void coldBuildFailureServesValidHomepageOnlyFallback() throws Exception {
        when(goodsCountService.countsByRoot()).thenThrow(new RuntimeException("db down"));

        Document doc = parse(service.sitemap());

        assertThat(texts(doc, "loc")).containsExactly("https://trovemo.com/");
    }

    // ---------------- subcategory + season landings (2026-08-26) ----------------

    @Test
    void emitsSubcategoryLandingsThatHaveGoodsAndSkipsTheEmptyOnes() throws Exception {
        stubCatalog();
        // 200 has stock, 201 is empty, 202 is soft-deleted.
        LitemallCategory deleted = category(202, "Retired Aisle");
        deleted.setDeleted(true);
        when(categoryService.queryByPid(100)).thenReturn(List.of(
                category(200, "Outdoor Lighting"), category(201, "Empty Shelf"), deleted));
        when(goodsCountService.countOnSaleInSubtree(200)).thenReturn(108L);
        when(goodsCountService.countOnSaleInSubtree(201)).thenReturn(0L);

        List<String> locs = texts(parse(service.sitemap()), "loc");

        assertThat(locs).contains("https://trovemo.com/category/200");
        assertThat(locs).noneMatch(loc -> loc.contains("/category/201"));
        assertThat(locs).noneMatch(loc -> loc.contains("/category/202"));
    }

    @Test
    void doesNotDescendIntoAnEmptyRoot() throws Exception {
        stubCatalog();

        service.sitemap();

        // Root 101 has no on-sale goods, so its children are never even counted.
        Mockito.verify(categoryService, Mockito.never()).queryByPid(101);
    }

    @Test
    void emitsTheActiveSeasonPage() throws Exception {
        stubCatalog();
        when(pageService.activeByCategory("season")).thenReturn(Map.of("id", 5, "name", "Autumn"));

        List<String> locs = texts(parse(service.sitemap()), "loc");

        assertThat(locs).contains("https://trovemo.com/page/5");
    }

    @Test
    void omitsTheSeasonPageWhenNoneIsActive() throws Exception {
        stubCatalog();
        when(pageService.activeByCategory("season")).thenReturn(null);

        List<String> locs = texts(parse(service.sitemap()), "loc");

        assertThat(locs).noneMatch(loc -> loc.contains("/page/"));
    }

    @Test
    void anUnreadableSeasonPageCostsOneUrlNotTheWholeSitemap() throws Exception {
        stubCatalog();
        when(pageService.activeByCategory("season")).thenThrow(new IllegalStateException("db down"));

        List<String> locs = texts(parse(service.sitemap()), "loc");

        assertThat(locs).noneMatch(loc -> loc.contains("/page/"));
        // The catalogue still ships.
        assertThat(locs).contains("https://trovemo.com/", "https://trovemo.com/product/1-wireless-earbuds");
    }

    private void stubCatalog() {
        when(goodsCountService.countsByRoot()).thenReturn(Map.of(100, 5L, 101, 0L));
        when(categoryService.queryL1()).thenReturn(List.of(
                category(100, "Women's Clothing"), category(101, "Empty Corner")));
        when(categoryService.queryByPid(anyInt())).thenReturn(List.of());
        when(goodsService.querySelective(any(), any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(List.of(
                        goods(1, "Wireless Earbuds", true, LocalDateTime.of(2026, 7, 28, 3, 0, 5)),
                        goods(2, "Retired Lamp", false, LocalDateTime.of(2026, 7, 1, 0, 0)),
                        goods(3, "蓝牙耳机", true, null)));
    }

    private static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static List<String> texts(Document doc, String tag) {
        NodeList nodes = doc.getElementsByTagNameNS("http://www.sitemaps.org/schemas/sitemap/0.9", tag);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            values.add(nodes.item(i).getTextContent());
        }
        return values;
    }

    private static LitemallGoods goods(int id, String name, boolean onSale, LocalDateTime updateTime) {
        LitemallGoods goods = new LitemallGoods();
        goods.setId(id);
        goods.setName(name);
        goods.setIsOnSale(onSale);
        goods.setUpdateTime(updateTime);
        goods.setDeleted(false);
        return goods;
    }

    private static LitemallCategory category(int id, String name) {
        LitemallCategory category = new LitemallCategory();
        category.setId(id);
        category.setName(name);
        category.setLevel("L1");
        category.setDeleted(false);
        return category;
    }
}
