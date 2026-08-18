package org.linlinjava.litemall.goods.domain.service.elastic;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private LitemallProductIndexingService service;

    @BeforeEach
    public void setUp() {
        LitemallBrandService brandService = mock(LitemallBrandService.class);
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
}
