package org.linlinjava.litemall.goods.application.pricing;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallCategoryMarginMapper;
import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallCategoryMargin;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.goods.application.search.CjPricing;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Margin resolution order (Wave 14): the L1-root override when one exists — resolved from any
 * subtree category or a linked CJ leaf UUID — else the global
 * {@code spring.cjdropship.pricing.margin}; every unknown/failed lookup falls open to global.
 */
public class CategoryMarginResolverTest {

    private static final int ROOT = 100;
    private static final int LEAF = 110;
    private static final String CJ_LEAF = "5E65-UUID";

    private LitemallCategoryMarginMapper marginMapper;
    private LitemallCjLinkageMapper linkageMapper;
    private LitemallCategoryService categoryService;
    private CategoryMarginResolver resolver;
    private CJDropshippingConfig config;

    @BeforeEach
    public void setUp() {
        marginMapper = mock(LitemallCategoryMarginMapper.class);
        linkageMapper = mock(LitemallCjLinkageMapper.class);
        categoryService = mock(LitemallCategoryService.class);
        config = new CJDropshippingConfig(); // global margin 1.25
        resolver = new CategoryMarginResolver(marginMapper, linkageMapper, categoryService, config);

        LitemallCategory root = new LitemallCategory();
        root.setId(ROOT);
        LitemallCategory leaf = new LitemallCategory();
        leaf.setId(LEAF);
        when(categoryService.queryL1()).thenReturn(List.of(root));
        when(categoryService.queryByPid(ROOT)).thenReturn(List.of(leaf));
        when(categoryService.queryByPid(LEAF)).thenReturn(List.of());
        when(marginMapper.selectAll()).thenReturn(List.of(override(ROOT, "1.50")));
    }

    private static LitemallCategoryMargin override(int categoryId, String margin) {
        LitemallCategoryMargin m = new LitemallCategoryMargin();
        m.setCategoryId(categoryId);
        m.setMargin(new BigDecimal(margin));
        return m;
    }

    @Test
    public void leafCategoryResolvesToItsRootOverride() {
        assertEquals(new BigDecimal("1.50"), resolver.effectiveForCategory(LEAF));
    }

    @Test
    public void unknownCategoryFallsOpenToGlobal() {
        assertEquals(new BigDecimal("1.25"), resolver.effectiveForCategory(999));
    }

    @Test
    public void linkedCjLeafResolvesThroughTheMirrorToTheOverride() {
        when(linkageMapper.findCjCategoryIdByCjId(CJ_LEAF)).thenReturn(LEAF);
        assertEquals(new BigDecimal("1.50"), resolver.effectiveForCjLeaf(CJ_LEAF));
    }

    @Test
    public void unlinkedCjLeafFallsOpenToGlobal() {
        when(linkageMapper.findCjCategoryIdByCjId(CJ_LEAF)).thenReturn(null);
        assertEquals(new BigDecimal("1.25"), resolver.effectiveForCjLeaf(CJ_LEAF));
    }

    @Test
    public void noOverridesMeansGlobalWithoutLinkageLookups() {
        when(marginMapper.selectAll()).thenReturn(List.of());
        assertEquals(new BigDecimal("1.25"), resolver.effectiveForCjLeaf(CJ_LEAF));
        org.mockito.Mockito.verify(linkageMapper, org.mockito.Mockito.never())
                .findCjCategoryIdByCjId(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void invalidateMakesAPutVisibleImmediately() {
        assertEquals(new BigDecimal("1.50"), resolver.effectiveForRoot(ROOT));
        when(marginMapper.selectAll()).thenReturn(List.of(override(ROOT, "2.00")));
        resolver.invalidateOverrides();
        assertEquals(new BigDecimal("2.00"), resolver.effectiveForRoot(ROOT));
    }

    @Test
    public void brokenLookupFallsOpenToGlobal() {
        when(categoryService.queryL1()).thenThrow(new RuntimeException("db down"));
        assertEquals(new BigDecimal("1.25"), resolver.effectiveForCategory(LEAF));
    }

    @Test
    public void cjPricingAppliesTheExplicitMarginAtTwoDecimals() {
        CjPricing pricing = new CjPricing(config);
        assertEquals(new BigDecimal("15.00"),
                pricing.retail(new BigDecimal("10.00"), new BigDecimal("1.50")));
        // null margin falls back to global 1.25
        assertEquals(new BigDecimal("12.50"), pricing.retail(new BigDecimal("10.00"), null));
    }

    @Test
    public void cjPricingWithResolverPricesPerCategory() {
        when(linkageMapper.findCjCategoryIdByCjId(CJ_LEAF)).thenReturn(LEAF);
        CjPricing pricing = new CjPricing(config, resolver);
        assertEquals(new BigDecimal("15.00"),
                pricing.retailForCjLeaf(new BigDecimal("10.00"), CJ_LEAF));
        assertEquals(new BigDecimal("15.00"),
                pricing.retailForCategory(new BigDecimal("10.00"), LEAF));
        assertEquals(new BigDecimal("12.50"),
                pricing.retailForCategory(new BigDecimal("10.00"), 999));
    }
}
