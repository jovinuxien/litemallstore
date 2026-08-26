package org.linlinjava.litemall.goods.domain.service.elastic;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallBrand;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallBrandService;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallGoodsAttributeService;
import org.linlinjava.litemall.db.service.LitemallGoodsProductService;
import org.linlinjava.litemall.db.service.LitemallSeckillService;
import org.linlinjava.litemall.goods.domain.model.valueobjects.elastic.ProductDocument;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSearchProperties;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The always-emit rule for the promo flags: {@code coupon_flag} and {@code groupon_flag}
 * ride EVERY document as explicit 0/1 — never absent — so a promo-less full reindex can
 * never drop them from OCS's mapping resolution (the deal-fields precedent).
 */
public class LitemallProductIndexingServiceTest {

    private CouponSignalResolver couponSignalResolver;
    private GrouponSignalResolver grouponSignalResolver;
    private EuStockSignalResolver euStockSignalResolver;
    private LitemallBrandService brandService;
    private LitemallProductIndexingService service;

    @BeforeEach
    public void setUp() {
        brandService = mock(LitemallBrandService.class);
        LitemallCategoryService categoryService = mock(LitemallCategoryService.class);
        LitemallGoodsAttributeService attributeService = mock(LitemallGoodsAttributeService.class);
        LitemallGoodsProductService productService = mock(LitemallGoodsProductService.class);
        LitemallSeckillService seckillService = mock(LitemallSeckillService.class);
        couponSignalResolver = mock(CouponSignalResolver.class);
        grouponSignalResolver = mock(GrouponSignalResolver.class);
        euStockSignalResolver = mock(EuStockSignalResolver.class);
        when(attributeService.queryByGid(anyInt())).thenReturn(List.of());
        when(productService.queryByGid(anyInt())).thenReturn(List.of());
        when(seckillService.findLiveByGoodsId(anyInt())).thenReturn(null);
        service = new LitemallProductIndexingService(brandService, categoryService,
                attributeService, productService, seckillService,
                couponSignalResolver, grouponSignalResolver, euStockSignalResolver,
                new LitemallSearchProperties());
    }

    private static LitemallGoods goods(int id) {
        LitemallGoods goods = new LitemallGoods();
        goods.setId(id);
        goods.setName("Ice Silk Boxer Briefs");
        goods.setRetailPrice(new BigDecimal("9.99"));
        return goods;
    }

    @Test
    public void promoFlagsAlwaysEmitOneWhenSignalled() {
        when(couponSignalResolver.couponFlag(anyInt(), any())).thenReturn(1);
        when(grouponSignalResolver.grouponFlag(anyInt())).thenReturn(1);
        ProductDocument doc = service.createProductDocument(goods(42));
        assertEquals(1, doc.getCouponFlag());
        assertEquals(1, doc.getGrouponFlag());
        Mockito.verify(grouponSignalResolver).grouponFlag(42);
    }

    @Test
    public void promoFlagsAlwaysEmitZeroNeverAbsentWhenUnsignalled() {
        when(couponSignalResolver.couponFlag(anyInt(), any())).thenReturn(0);
        when(grouponSignalResolver.grouponFlag(anyInt())).thenReturn(0);
        ProductDocument doc = service.createProductDocument(goods(42));
        assertEquals(0, doc.getCouponFlag(), "coupon_flag must be 0, not absent");
        assertEquals(0, doc.getGrouponFlag(), "groupon_flag must be 0, not absent");
    }

    // ---- Wave-27 eu_flag ---------------------------------------------------

    /** The reading lives on the CJ snapshot row, so the resolver is keyed on cj_pid, not goods id. */
    @Test
    public void euFlagIsResolvedFromTheProductsCjPid() {
        when(euStockSignalResolver.euFlag("pid-de-1")).thenReturn(1);
        LitemallGoods goods = goods(42);
        goods.setCjPid("pid-de-1");

        ProductDocument doc = service.createProductDocument(goods);

        assertEquals(1, doc.getEuFlag());
        Mockito.verify(euStockSignalResolver).euFlag("pid-de-1");
    }

    @Test
    public void euFlagAlwaysEmitsZeroNeverAbsentWhenUnsignalled() {
        when(euStockSignalResolver.euFlag(any())).thenReturn(0);

        ProductDocument doc = service.createProductDocument(goods(42));

        assertEquals(0, doc.getEuFlag(), "eu_flag must be 0, not absent");
    }

    // ---- the Wave-25 curation gate, at the index ----------------------------
    //
    // These pin the fix for a leak measured live on trovemo.com 2026-08-26: the indexer emitted
    // brand with no gate at all, so raw CJ supplier legal entities ("Yiwu Ruijia Auto Supplies
    // Co., Ltd.") were offered in the SPA's "Brand" facet and in search autocomplete, which
    // sources the same field. The index carries a BARE brand claim with no room for a "Sold by"
    // qualifier, so the bar here is isConsumerBrand, not merely isDisplayable.

    private LitemallGoods goodsWithBrand(int id, int brandId) {
        LitemallGoods goods = goods(id);
        goods.setBrandId(brandId);
        return goods;
    }

    private static LitemallBrand brand(String name, Integer kind, Boolean displayEnabled, Boolean deleted) {
        LitemallBrand brand = new LitemallBrand();
        brand.setId(7);
        brand.setName(name);
        brand.setKind(kind == null ? null : kind.byteValue());
        brand.setDisplayEnabled(displayEnabled);
        brand.setDeleted(deleted);
        return brand;
    }

    @Test
    public void curatedConsumerBrandIsIndexed() {
        when(brandService.findById(7)).thenReturn(brand("Stoneline", 0, true, false));

        assertEquals("Stoneline", service.createProductDocument(goodsWithBrand(42, 7)).getBrand());
    }

    @Test
    public void uncuratedSupplierIsNotIndexed() {
        when(brandService.findById(7))
                .thenReturn(brand("Yiwu Ruijia Auto Supplies Co., Ltd.", 1, false, false));

        assertNull(service.createProductDocument(goodsWithBrand(42, 7)).getBrand(),
                "a disabled supplier row must never reach the brand facet");
    }

    /**
     * The decision that separates this from {@code isDisplayable}: an admin ENABLED supplier
     * store is legitimately shown on the PDP as "Sold by", but the facet is headed "Brand", so
     * it still must not appear there. Three such rows are display-enabled in production.
     */
    @Test
    public void enabledSupplierStoreIsStillNotIndexedAsABrand() {
        when(brandService.findById(7)).thenReturn(brand("EVERGREEN SHOP LLC", 1, true, false));

        assertNull(service.createProductDocument(goodsWithBrand(42, 7)).getBrand(),
                "kind=1 is a store, and the facet claims 'Brand'");
    }

    @Test
    public void deletedBrandIsNotIndexed() {
        when(brandService.findById(7)).thenReturn(brand("Stoneline", 0, true, true));

        assertNull(service.createProductDocument(goodsWithBrand(42, 7)).getBrand());
    }

    /** Attribution is decoration: a dangling brand_id degrades to no brand, never to a throw. */
    @Test
    public void missingBrandRowDegradesToNoBrand() {
        when(brandService.findById(7)).thenReturn(null);

        assertNull(service.createProductDocument(goodsWithBrand(42, 7)).getBrand());
    }
}
