package org.linlinjava.litemall.goods.application.search;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallBrandMapper;
import org.linlinjava.litemall.db.dao.LitemallCategoryMapper;
import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsAttributeMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsProductMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsSpecificationMapper;
import org.linlinjava.litemall.db.dao.LitemallSeckillMapper;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.goods.application.pricing.CategoryMarginResolver;
import org.linlinjava.litemall.goods.infrastructure.acl.adapter.CjProductToNativeAdapter;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wave-14 promote-time reprice: once the local category is resolved, retail/counter and every
 * costed SKU converge on cost × the category's EFFECTIVE margin; cost-less SKUs follow the
 * product retail (the stale-basis intent); goods without a captured cost are left untouched.
 */
public class CjPromoteRepriceTest {

    private CategoryMarginResolver resolver;
    private CjProductPromotionService service;

    @BeforeEach
    public void setUp() {
        resolver = mock(CategoryMarginResolver.class);
        CJDropshippingConfig config = new CJDropshippingConfig();
        service = new CjProductPromotionService(
                mock(LitemallCjLinkageMapper.class), mock(LitemallGoodsMapper.class),
                mock(LitemallGoodsProductMapper.class), mock(LitemallGoodsAttributeMapper.class),
                mock(LitemallGoodsSpecificationMapper.class), mock(LitemallCategoryMapper.class),
                mock(LitemallBrandMapper.class), mock(LitemallSeckillMapper.class),
                mock(CjProductToNativeAdapter.class), mock(CjCategoryTreeSyncService.class),
                config, new CjPricing(config, resolver), List.of(),
                mock(PlatformTransactionManager.class));
    }

    private static LitemallGoods goods(String cost, String retail, Integer categoryId) {
        LitemallGoods g = new LitemallGoods();
        g.setCost(cost == null ? null : new BigDecimal(cost));
        g.setRetailPrice(retail == null ? null : new BigDecimal(retail));
        g.setCounterPrice(g.getRetailPrice());
        g.setCategoryId(categoryId);
        return g;
    }

    private static LitemallGoodsProduct sku(String cost, String price) {
        LitemallGoodsProduct p = new LitemallGoodsProduct();
        p.setCost(cost == null ? null : new BigDecimal(cost));
        p.setPrice(price == null ? null : new BigDecimal(price));
        return p;
    }

    @Test
    public void costedGoodsConvergesOnTheCategoryEffectiveMargin() {
        when(resolver.effectiveForCategory(1005)).thenReturn(new BigDecimal("1.50"));
        LitemallGoods g = goods("10.00", "12.50", 1005); // priced at the old global 1.25
        LitemallGoodsProduct costed = sku("8.00", "10.00");
        LitemallGoodsProduct costless = sku(null, "12.50");

        service.repriceForCategory(g, List.of(costed, costless));

        assertEquals(new BigDecimal("15.00"), g.getRetailPrice());
        assertEquals(new BigDecimal("15.00"), g.getCounterPrice()); // CJ convention counter == retail
        assertEquals(new BigDecimal("12.00"), costed.getPrice());   // 8.00 × 1.50
        assertEquals(new BigDecimal("15.00"), costless.getPrice()); // follows product retail
    }

    @Test
    public void goodsWithoutCapturedCostIsNeverFakeRepriced() {
        when(resolver.effectiveForCategory(1005)).thenReturn(new BigDecimal("1.50"));
        LitemallGoods g = goods(null, "260.91", 1005); // legacy ×14.4 price, no cost yet
        LitemallGoodsProduct p = sku(null, "260.91");

        service.repriceForCategory(g, List.of(p));

        assertEquals(new BigDecimal("260.91"), g.getRetailPrice());
        assertEquals(new BigDecimal("260.91"), p.getPrice());
    }
}
