package org.linlinjava.litemall.goods.application.inventoryflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.goods.application.goods.CatalogGoodsCountService;
import org.linlinjava.litemall.goods.infrastructure.configuration.InventoryFlowProperties;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rollup ranking: categories sorted by potentialProfit desc, zero-on-sale roots dropped,
 * TTL'd snapshot served without re-querying, empty catalog degrades to an empty list.
 */
public class CategoryInsightCacheTest {

    private InsightMapper insightMapper;
    private LitemallCategoryService categoryService;
    private CatalogGoodsCountService countService;
    private CategoryInsightCache cache;

    @BeforeEach
    public void setUp() {
        insightMapper = mock(InsightMapper.class);
        categoryService = mock(LitemallCategoryService.class);
        countService = mock(CatalogGoodsCountService.class);
        cache = new CategoryInsightCache(insightMapper, categoryService, countService,
                new LitemallGoodsProperties(), new InventoryFlowProperties());
    }

    private static LitemallCategory root(int id, String name) {
        LitemallCategory c = new LitemallCategory();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private static Map<String, Object> agg(long onSale, String potentialProfit) {
        Map<String, Object> m = new HashMap<>();
        m.put("onSaleCount", onSale);
        m.put("newArrivals7d", 1L);
        m.put("stockUnits", 10L);
        m.put("lowStockCount", 0L);
        m.put("unavailableCount", 0L);
        m.put("avgMarginPct", new BigDecimal("20.00"));
        m.put("potentialProfit", potentialProfit == null ? null : new BigDecimal(potentialProfit));
        return m;
    }

    @Test
    public void ranksByPotentialProfitDescAndDropsEmptyRoots() {
        when(categoryService.queryL1()).thenReturn(
                List.of(root(1, "Clothing"), root(2, "Electronics"), root(3, "Empty")));
        when(countService.subtreeIds(anyInt())).thenAnswer(inv -> {
            Integer rootId = inv.getArgument(0);
            return List.of(rootId);
        });
        when(insightMapper.selectCategoryAgg(any(), any(), anyInt(), anyInt())).thenAnswer(inv -> {
            List<Integer> subtree = inv.getArgument(0);
            return switch (subtree.get(0)) {
                case 1 -> agg(5, "100.00");
                case 2 -> agg(3, "500.00");
                default -> agg(0, "0.00");
            };
        });

        List<Map<String, Object>> list = cache.get();

        assertEquals(2, list.size(), "zero-on-sale root must be dropped");
        assertEquals("Electronics", list.get(0).get("name"));
        assertEquals("Clothing", list.get(1).get("name"));
        assertEquals(new BigDecimal("500.00"), list.get(0).get("potentialProfit"));
    }

    @Test
    public void snapshotIsServedWithoutRequeryInsideTtl() {
        when(categoryService.queryL1()).thenReturn(List.of(root(1, "Clothing")));
        when(countService.subtreeIds(anyInt())).thenReturn(List.of(1));
        when(insightMapper.selectCategoryAgg(any(), any(), anyInt(), anyInt()))
                .thenReturn(agg(5, "100.00"));

        cache.get();
        cache.get();

        verify(insightMapper, times(1)).selectCategoryAgg(any(), any(), anyInt(), anyInt());
    }

    @Test
    public void emptyCatalogDegradesToEmptyList() {
        when(categoryService.queryL1()).thenReturn(List.of());

        assertTrue(cache.get().isEmpty());
    }
}
