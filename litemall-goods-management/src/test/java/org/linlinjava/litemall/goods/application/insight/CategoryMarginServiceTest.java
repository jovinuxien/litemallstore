package org.linlinjava.litemall.goods.application.insight;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.dao.LitemallCategoryMarginMapper;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.goods.application.goods.CatalogGoodsCountService;
import org.linlinjava.litemall.goods.application.pricing.CategoryMarginResolver;
import org.linlinjava.litemall.goods.infrastructure.configuration.InventoryFlowProperties;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Margin CRUD honesty: bounds 1.05–3.0 enforced, overrides key on L1 roots only, PUT/DELETE
 * invalidate the resolver so the change applies immediately, simulate is a pure read that
 * reports current vs simulated numbers side by side.
 */
public class CategoryMarginServiceTest {

    private static final int ROOT = 100;

    private LitemallCategoryMarginMapper marginMapper;
    private LitemallCategoryService categoryService;
    private CatalogGoodsCountService countService;
    private InsightMapper insightMapper;
    private CategoryMarginResolver resolver;
    private CategoryMarginService service;

    @BeforeEach
    public void setUp() {
        marginMapper = mock(LitemallCategoryMarginMapper.class);
        categoryService = mock(LitemallCategoryService.class);
        countService = mock(CatalogGoodsCountService.class);
        insightMapper = mock(InsightMapper.class);
        resolver = mock(CategoryMarginResolver.class);
        service = new CategoryMarginService(marginMapper, categoryService, countService,
                insightMapper, resolver, new InventoryFlowProperties());

        LitemallCategory root = new LitemallCategory();
        root.setId(ROOT);
        root.setPid(0);
        when(categoryService.findById(ROOT)).thenReturn(root);
        when(countService.subtreeIds(ROOT)).thenReturn(List.of(ROOT, 110));
    }

    @Test
    public void marginBelowBoundsIsRejected() {
        assertEquals(Integer.valueOf(402), service.put(ROOT, new BigDecimal("1.00")).errno());
        verify(marginMapper, never()).upsert(any());
    }

    @Test
    public void marginAboveBoundsIsRejected() {
        assertEquals(Integer.valueOf(402), service.put(ROOT, new BigDecimal("3.50")).errno());
    }

    @Test
    public void nonRootCategoryIsRejected() {
        LitemallCategory leaf = new LitemallCategory();
        leaf.setId(110);
        leaf.setPid(ROOT);
        when(categoryService.findById(110)).thenReturn(leaf);
        assertEquals(Integer.valueOf(402), service.put(110, new BigDecimal("1.50")).errno());
    }

    @Test
    public void putUpsertsAndInvalidatesTheResolver() {
        GovernanceResult result = service.put(ROOT, new BigDecimal("1.50"));

        assertNull(result.error());
        ArgumentCaptor<org.linlinjava.litemall.db.domain.LitemallCategoryMargin> captor =
                ArgumentCaptor.forClass(org.linlinjava.litemall.db.domain.LitemallCategoryMargin.class);
        verify(marginMapper).upsert(captor.capture());
        assertEquals(Integer.valueOf(ROOT), captor.getValue().getCategoryId());
        assertEquals(new BigDecimal("1.50"), captor.getValue().getMargin());
        verify(resolver).invalidateOverrides();
    }

    @Test
    public void deleteRemovesAndInvalidates() {
        when(marginMapper.deleteById(ROOT)).thenReturn(1);
        GovernanceResult result = service.delete(ROOT);
        assertNull(result.error());
        verify(resolver).invalidateOverrides();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();
        assertEquals(Boolean.TRUE, data.get("removed"));
    }

    @Test
    public void simulateIsAPureReadReportingBothMargins() {
        when(resolver.effectiveForRoot(ROOT)).thenReturn(new BigDecimal("1.25"));
        Map<String, Object> agg = new HashMap<>();
        agg.put("goodsCount", 10L);
        agg.put("avgPriceNow", new BigDecimal("12.50"));
        agg.put("avgPriceAt", new BigDecimal("15.00"));
        agg.put("potentialProfitNow", new BigDecimal("125.00"));
        agg.put("potentialProfitAt", new BigDecimal("250.00"));
        when(insightMapper.selectMarginSimulate(anyList(), eq(new BigDecimal("1.50")), anyInt()))
                .thenReturn(agg);

        GovernanceResult result = service.simulate(ROOT, new BigDecimal("1.50"));

        assertNull(result.error());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();
        assertEquals(new BigDecimal("1.25"), data.get("currentMargin"));
        assertEquals(new BigDecimal("1.50"), data.get("simulatedMargin"));
        assertEquals(new BigDecimal("15.00"), data.get("avgPriceAt"));
        verify(marginMapper, never()).upsert(any()); // pure read — no mutation
    }
}
