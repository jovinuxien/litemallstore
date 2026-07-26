package org.linlinjava.litemall.goods.application.goods;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallAd;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallAdService;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.infrastructure.configuration.HomeBannerProperties;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

public class HomeBannerServiceTest {

    private static final String CDN = "https://cf.cjdropshipping.com/pic/";
    private static final String OSS = "https://oss-cf.cjdropshipping.com/pic/";

    private LitemallAdService adService;
    private LitemallCategoryService categoryService;
    private LitemallGoodsService goodsService;
    private CatalogGoodsCountService goodsCountService;
    private HomeBannerProperties properties;
    private HomeBannerService service;

    @BeforeEach
    void setup() {
        adService = Mockito.mock(LitemallAdService.class);
        categoryService = Mockito.mock(LitemallCategoryService.class);
        goodsService = Mockito.mock(LitemallGoodsService.class);
        goodsCountService = Mockito.mock(CatalogGoodsCountService.class);
        properties = new HomeBannerProperties();
        service = new HomeBannerService(adService, categoryService, goodsService, goodsCountService, properties);
        Mockito.when(adService.queryIndex()).thenReturn(List.of());
    }

    private static LitemallAd ad(String url) {
        LitemallAd a = new LitemallAd();
        a.setUrl(url);
        return a;
    }

    private static LitemallCategory category(int id, String name) {
        LitemallCategory c = new LitemallCategory();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private static LitemallGoods goods(String picUrl) {
        LitemallGoods g = new LitemallGoods();
        g.setPicUrl(picUrl);
        return g;
    }

    /** Stub one root's full derivation path: counts entry, L1 name row, subtree, newest goods. */
    private void stubRoot(int id, String name, List<LitemallGoods> newest) {
        Mockito.when(goodsCountService.subtreeIds(id)).thenReturn(List.of(id));
        Mockito.when(goodsService.queryByCategory(eq(List.of(id)), eq(1), anyInt())).thenReturn(newest);
        stubL1(category(id, name));
    }

    private final List<LitemallCategory> l1Rows = new java.util.ArrayList<>();

    private void stubL1(LitemallCategory row) {
        l1Rows.add(row);
        Mockito.when(categoryService.queryL1()).thenReturn(l1Rows);
    }

    @Test
    public void topRootsByCountWithThresholdAndDeterministicTieBreak() {
        properties.setTopN(2);
        properties.setMinOnSaleGoods(20);
        Map<Integer, Long> counts = new LinkedHashMap<>();
        counts.put(10, 100L);
        counts.put(20, 100L); // ties with 10 → id asc keeps 10
        counts.put(30, 5L);   // below threshold, never a banner
        counts.put(40, 300L);
        Mockito.when(goodsCountService.countsByRoot()).thenReturn(counts);
        stubRoot(10, "Ten", List.of(goods(CDN + "10.jpg")));
        stubRoot(20, "Twenty", List.of(goods(CDN + "20.jpg")));
        stubRoot(40, "Forty", List.of(goods(OSS + "40.jpg")));

        List<LitemallAd> banners = service.homeBanners();

        assertThat(banners).extracting(LitemallAd::getName).containsExactly("Forty", "Ten");
        Mockito.verify(goodsService, Mockito.never()).queryByCategory(eq(List.of(30)), anyInt(), anyInt());
    }

    @Test
    public void heroSkipsForeignHostsAndRootWithoutCdnPicIsDropped() {
        properties.setTopN(5);
        Mockito.when(goodsCountService.countsByRoot()).thenReturn(Map.of(1, 500L, 2, 400L));
        // Root 1: newest goods carry null / yanxuan / aliyuncs pics before the first CDN-hosted one.
        stubRoot(1, "Mixed", Arrays.asList(
                goods(null),
                goods("http://yanxuan.nosdn.127.net/a.jpg"),
                goods("https://img.alicdn.aliyuncs.com/b.jpg"),
                goods(CDN + "hero.jpg")));
        // Root 2: nothing CDN-hosted at all → no banner, no broken image.
        stubRoot(2, "Foreign", List.of(goods("https://img.alicdn.aliyuncs.com/c.jpg")));

        List<LitemallAd> banners = service.homeBanners();

        assertThat(banners).hasSize(1);
        assertThat(banners.get(0).getName()).isEqualTo("Mixed");
        assertThat(banners.get(0).getUrl()).isEqualTo(CDN + "hero.jpg");
    }

    @Test
    public void manualRowsComeFirstAndPlainHttpSeedsAreDropped() {
        LitemallAd httpsRow = ad("https://example.com/promo.jpg");
        LitemallAd relativeRow = ad("/uploads/promo.jpg");
        Mockito.when(adService.queryIndex()).thenReturn(Arrays.asList(
                ad("http://yanxuan.nosdn.127.net/65091.jpg"), // the seed-row shape: served on master, dropped now
                httpsRow,
                relativeRow,
                ad(""),
                ad(null)));
        Mockito.when(goodsCountService.countsByRoot()).thenReturn(Map.of(7, 900L));
        stubRoot(7, "Pets", List.of(goods(CDN + "pet.jpg")));

        List<LitemallAd> banners = service.homeBanners();

        assertThat(banners).hasSize(3);
        assertThat(banners.get(0)).isSameAs(httpsRow);
        assertThat(banners.get(1)).isSameAs(relativeRow);
        assertThat(banners.get(2).getName()).isEqualTo("Pets");
    }

    @Test
    public void emptyCatalogFallsBackToManualOnlyAndCachesTheEmptyResult() {
        Mockito.when(adService.queryIndex()).thenReturn(List.of(ad("https://example.com/a.jpg")));
        Mockito.when(goodsCountService.countsByRoot()).thenReturn(Map.of());

        assertThat(service.homeBanners()).hasSize(1);
        assertThat(service.homeBanners()).hasSize(1);

        // Empty derivations are cached for a full TTL — no per-request re-derive storm.
        Mockito.verify(goodsCountService, Mockito.times(1)).countsByRoot();
    }

    @Test
    public void derivationFailureDegradesToManualOnlyWithoutThrowing() {
        Mockito.when(adService.queryIndex()).thenReturn(List.of(ad("https://example.com/a.jpg")));
        Mockito.when(goodsCountService.countsByRoot()).thenThrow(new RuntimeException("db down"));

        assertThat(service.homeBanners()).hasSize(1);
        assertThat(service.homeBanners()).hasSize(1);

        // The failed build is snapshotted too; retry waits for the TTL, not the next request.
        Mockito.verify(goodsCountService, Mockito.times(1)).countsByRoot();
    }

    @Test
    public void disabledFlagServesFilteredManualOnlyAndNeverDerives() {
        properties.setEnabled(false);
        Mockito.when(adService.queryIndex()).thenReturn(Arrays.asList(
                ad("http://yanxuan.nosdn.127.net/65091.jpg"),
                ad("https://example.com/a.jpg")));

        List<LitemallAd> banners = service.homeBanners();

        // Even with generation off, the mixed-content http seeds must not resurface.
        assertThat(banners).hasSize(1);
        assertThat(banners.get(0).getUrl()).isEqualTo("https://example.com/a.jpg");
        Mockito.verifyZeroInteractions(goodsCountService, categoryService, goodsService);
    }

    @Test
    public void generatedBannerMatchesTheWave11Contract() {
        Mockito.when(goodsCountService.countsByRoot()).thenReturn(Map.of(1036012, 1300L));
        stubRoot(1036012, "Women's Clothing", List.of(goods(CDN + "hero.jpg")));

        List<LitemallAd> banners = service.homeBanners();

        assertThat(banners).hasSize(1);
        LitemallAd banner = banners.get(0);
        assertThat(banner.getId()).isEqualTo(-1036012);          // synthetic id, collision-proof
        assertThat(banner.getName()).isEqualTo("Women's Clothing");
        assertThat(banner.getUrl()).isEqualTo(CDN + "hero.jpg"); // raw CJ URL; the edge rewrites to /_cdn
        assertThat(banner.getLink()).isEqualTo("/category/1036012");
        assertThat(banner.getContent()).isEqualTo("1,300 products");
        assertThat(banner.getPosition()).isEqualTo((byte) 1);
        assertThat(banner.getEnabled()).isTrue();
    }
}
