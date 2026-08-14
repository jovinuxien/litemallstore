package org.linlinjava.litemall.goods.interfaces.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.goods.application.goods.CatalogGoodsCountService;
import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.application.goods.LitemallGoodsManagementService;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wave 26: narrowing the storefront to an anchor leaves the other L1 roots on sale-less. This is
 * the CUSTOMER-facing category nav, so an unfiltered list renders tiles that lead to empty pages
 * — the exact failure the Wave-26 spec forbids. Also pins the degrade path: an all-zero count map
 * serves the unfiltered list rather than an empty nav.
 */
public class CatalogEmptyCategoryFilterTest {

    private static final int ANCHOR = 1036143;
    private static final int EMPTIED = 1036012;

    private LitemallGoodsManagementService goodsApi;
    private CatalogGoodsCountService countService;
    private LitemallCatalogController controller;

    @BeforeEach
    public void setUp() {
        goodsApi = mock(LitemallGoodsManagementService.class);
        countService = mock(CatalogGoodsCountService.class);
        controller = new LitemallCatalogController();
        ReflectionTestUtils.setField(controller, "goodsManagementServiceApi", goodsApi);
        ReflectionTestUtils.setField(controller, "catalogGoodsCountService", countService);
        when(goodsApi.getFirstLevelCategories()).thenReturn(roots());
        when(goodsApi.queryByPid(org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
    }

    private List<LitemallCategoryAggregate> roots() {
        List<LitemallCategoryAggregate> list = new ArrayList<>();
        list.add(root(ANCHOR, "Home, Garden & Furniture"));
        list.add(root(EMPTIED, "Women's Clothing"));
        return list;
    }

    private LitemallCategoryAggregate root(int id, String name) {
        LitemallCategoryAggregate aggregate = new LitemallCategoryAggregate();
        aggregate.setCategoryId(new LitemallCategoryId(id));
        aggregate.setCategoryName(name);
        return aggregate;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Object response) {
        Map<String, Object> envelope = (Map<String, Object>) response;
        return (Map<String, Object>) envelope.get("data");
    }

    @Test
    public void firstCategoriesHidesRootsWithNoOnSaleGoods() {
        when(countService.countsByRoot()).thenReturn(Map.of(ANCHOR, 2200L, EMPTIED, 0L));

        @SuppressWarnings("unchecked")
        List<LitemallCategoryAggregate> list =
                (List<LitemallCategoryAggregate>) data(controller.getFirstCategory()).get("l1CatList");

        assertEquals(1, list.size());
        assertEquals(ANCHOR, list.get(0).getCategoryId().getId());
    }

    @Test
    public void queryAllHidesRootsWithNoOnSaleGoods() {
        when(countService.countsByRoot()).thenReturn(Map.of(ANCHOR, 2200L, EMPTIED, 0L));

        @SuppressWarnings("unchecked")
        List<LitemallCategoryAggregate> list =
                (List<LitemallCategoryAggregate>) data(controller.queryAll()).get("categoryList");

        assertEquals(1, list.size());
        assertEquals(ANCHOR, list.get(0).getCategoryId().getId());
    }

    @Test
    public void aRootMissingFromTheCountMapIsTreatedAsEmpty() {
        when(countService.countsByRoot()).thenReturn(Map.of(ANCHOR, 2200L));

        @SuppressWarnings("unchecked")
        List<LitemallCategoryAggregate> list =
                (List<LitemallCategoryAggregate>) data(controller.getFirstCategory()).get("l1CatList");

        assertEquals(1, list.size());
    }

    @Test
    public void allZeroCountsServeTheUnfilteredNavRatherThanAnEmptyOne() {
        when(countService.countsByRoot()).thenReturn(Map.of());

        @SuppressWarnings("unchecked")
        List<LitemallCategoryAggregate> list =
                (List<LitemallCategoryAggregate>) data(controller.getFirstCategory()).get("l1CatList");

        assertEquals(2, list.size(), "a nav with no categories at all is a worse lie than a thin one");
        assertTrue(list.stream().anyMatch(c -> c.getCategoryId().getId() == EMPTIED));
    }
}
