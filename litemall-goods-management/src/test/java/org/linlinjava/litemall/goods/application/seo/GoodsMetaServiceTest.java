package org.linlinjava.litemall.goods.application.seo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GoodsMetaServiceTest {

    private LitemallGoodsService goodsService;
    private LitemallCategoryService categoryService;
    private GoodsMetaService service;

    @BeforeEach
    void setup() {
        goodsService = Mockito.mock(LitemallGoodsService.class);
        categoryService = Mockito.mock(LitemallCategoryService.class);
        service = new GoodsMetaService(goodsService, categoryService, new LitemallGoodsProperties());
    }

    @Test
    void servesTheFullContractShapeWithIsoStringUpdateTime() {
        when(goodsService.findById(7)).thenReturn(goods(7));
        when(categoryService.findById(100)).thenReturn(category(100, "Women's Clothing"));

        Map<String, Object> meta = service.meta(7).orElseThrow();

        assertThat(meta.keySet()).containsExactly(
                "id", "name", "brief", "picUrl", "retailPrice", "currency", "onSale",
                "rating", "reviewCount", "categoryId", "categoryName", "updateTime");
        assertThat(meta)
                .containsEntry("id", 7)
                .containsEntry("name", "Wireless Earbuds")
                .containsEntry("brief", "Great sound")
                .containsEntry("picUrl", "https://cf.cjdropshipping.com/x.jpg")
                .containsEntry("retailPrice", new BigDecimal("19.99"))
                .containsEntry("currency", "USD")
                .containsEntry("onSale", true)
                .containsEntry("rating", new BigDecimal("4.5"))
                .containsEntry("reviewCount", 12)
                .containsEntry("categoryId", 100)
                .containsEntry("categoryName", "Women's Clothing");
        // The contract field must be an ISO-8601 STRING — the module's ObjectMapper would
        // write a LocalDateTime value as an array.
        assertThat(meta.get("updateTime")).isEqualTo("2026-07-28T03:00:05");
    }

    @Test
    void neverRatedGoodsKeepNullRatingAndZeroReviewCount() {
        LitemallGoods goods = goods(7);
        goods.setRating(null);
        goods.setReviewCount(null);
        when(goodsService.findById(7)).thenReturn(goods);
        when(categoryService.findById(100)).thenReturn(category(100, "Women's Clothing"));

        Map<String, Object> meta = service.meta(7).orElseThrow();

        assertThat(meta.get("rating")).isNull();
        assertThat(meta.get("reviewCount")).isEqualTo(0);
    }

    @Test
    void missingCategoryYieldsNullNameWithoutFailing() {
        LitemallGoods orphan = goods(7);
        orphan.setCategoryId(null);
        when(goodsService.findById(7)).thenReturn(orphan);

        assertThat(service.meta(7).orElseThrow().get("categoryName")).isNull();
        verify(categoryService, never()).findById(Mockito.any());

        LitemallGoods dangling = goods(8);
        when(goodsService.findById(8)).thenReturn(dangling);
        when(categoryService.findById(100)).thenReturn(null);
        assertThat(service.meta(8).orElseThrow().get("categoryName")).isNull();
    }

    @Test
    void offSaleGoodsAreServedWithOnSaleFalse() {
        LitemallGoods goods = goods(7);
        goods.setIsOnSale(false);
        when(goodsService.findById(7)).thenReturn(goods);
        when(categoryService.findById(100)).thenReturn(category(100, "Women's Clothing"));

        assertThat(service.meta(7).orElseThrow().get("onSale")).isEqualTo(false);
    }

    @Test
    void missingOrDeletedGoodsYieldEmpty() {
        when(goodsService.findById(404)).thenReturn(null); // findById binds deleted=false
        assertThat(service.meta(404)).isEqualTo(Optional.empty());
    }

    @Test
    void secondReadInsideTtlServesFromCache() {
        when(goodsService.findById(7)).thenReturn(goods(7));
        when(categoryService.findById(100)).thenReturn(category(100, "Women's Clothing"));

        Map<String, Object> first = service.meta(7).orElseThrow();
        Map<String, Object> second = service.meta(7).orElseThrow();

        assertThat(second).isSameAs(first);
        verify(goodsService, times(1)).findById(7);
    }

    private static LitemallGoods goods(int id) {
        LitemallGoods goods = new LitemallGoods();
        goods.setId(id);
        goods.setName("Wireless Earbuds");
        goods.setBrief("Great sound");
        goods.setPicUrl("https://cf.cjdropshipping.com/x.jpg");
        goods.setRetailPrice(new BigDecimal("19.99"));
        goods.setIsOnSale(true);
        goods.setRating(new BigDecimal("4.5"));
        goods.setReviewCount(12);
        goods.setCategoryId(100);
        goods.setUpdateTime(LocalDateTime.of(2026, 7, 28, 3, 0, 5));
        goods.setDeleted(false);
        return goods;
    }

    private static LitemallCategory category(int id, String name) {
        LitemallCategory category = new LitemallCategory();
        category.setId(id);
        category.setName(name);
        category.setDeleted(false);
        return category;
    }
}
