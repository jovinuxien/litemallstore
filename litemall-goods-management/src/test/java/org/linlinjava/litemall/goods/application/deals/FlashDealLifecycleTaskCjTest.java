package org.linlinjava.litemall.goods.application.deals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.db.domain.LitemallSeckill;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.linlinjava.litemall.db.service.LitemallGoodsProductService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.db.service.LitemallSeckillService;
import org.linlinjava.litemall.goods.application.search.CjProductPromotionService;
import org.linlinjava.litemall.goods.application.search.SearchReindexService;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lifecycle facts under Wave 12: activation swaps EVERY SKU proportionally (multi-SKU CJ
 * goods included) capturing {o,s} per SKU; unwind restores per-SKU with the admin-wins
 * guard AND re-promotes a CJ goods from its snapshot so a mid-deal CJ reprice converges
 * immediately (the promote path withheld price writes while the deal was live).
 */
public class FlashDealLifecycleTaskCjTest {

    private LitemallSeckillService seckillService;
    private LitemallGoodsService goodsService;
    private LitemallGoodsProductService productService;
    private LitemallCjProductService cjProductService;
    private CjProductPromotionService cjPromotionService;
    private FlashDealLifecycleTask task;

    @BeforeEach
    public void setUp() throws Exception {
        seckillService = mock(LitemallSeckillService.class);
        goodsService = mock(LitemallGoodsService.class);
        productService = mock(LitemallGoodsProductService.class);
        SearchReindexService reindexService = mock(SearchReindexService.class);
        cjProductService = mock(LitemallCjProductService.class);
        cjPromotionService = mock(CjProductPromotionService.class);
        task = new FlashDealLifecycleTask(seckillService, goodsService, productService,
                reindexService, cjProductService, cjPromotionService);
        // The kill switch is an @Value field; tests wire it by reflection (house precedent).
        Field enabled = FlashDealLifecycleTask.class.getDeclaredField("enabled");
        enabled.setAccessible(true);
        enabled.set(task, true);
        when(seckillService.queryDueForActivation()).thenReturn(List.of());
        when(seckillService.queryDueForExpiry()).thenReturn(List.of());
        when(seckillService.querySwapped()).thenReturn(List.of());
    }

    private static LitemallGoodsProduct sku(int id, String price) {
        LitemallGoodsProduct sku = new LitemallGoodsProduct();
        sku.setId(id);
        sku.setPrice(new BigDecimal(price));
        return sku;
    }

    @Test
    public void activationSwapsEverySkuProportionallyAndCapturesOriginals() {
        LitemallSeckill deal = new LitemallSeckill();
        deal.setId(7);
        deal.setGoodsId(42);
        deal.setPrice(new BigDecimal("10.00"));
        deal.setStopTime(LocalDateTime.now().plusHours(4));
        when(seckillService.queryDueForActivation()).thenReturn(List.of(deal));

        LitemallGoods goods = new LitemallGoods();
        goods.setId(42);
        goods.setSource("cj");
        goods.setRetailPrice(new BigDecimal("12.50"));
        when(goodsService.findById(42)).thenReturn(goods);
        when(productService.queryByGid(42)).thenReturn(
                List.of(sku(14, "12.50"), sku(15, "25.00")));

        task.tick();

        ArgumentCaptor<LitemallGoodsProduct> skuPatches =
                ArgumentCaptor.forClass(LitemallGoodsProduct.class);
        verify(productService, org.mockito.Mockito.times(2)).updateById(skuPatches.capture());
        // Base SKU (== pre-deal retail) lands EXACTLY on the deal price; the other takes the
        // same proportional cut: 25.00 × (10.00 / 12.50) = 20.00.
        assertEquals(new BigDecimal("10.00"), skuPatches.getAllValues().get(0).getPrice());
        assertEquals(new BigDecimal("20.00"), skuPatches.getAllValues().get(1).getPrice());

        ArgumentCaptor<LitemallSeckill> dealPatch = ArgumentCaptor.forClass(LitemallSeckill.class);
        verify(seckillService).updateById(dealPatch.capture());
        assertTrue(Boolean.TRUE.equals(dealPatch.getValue().getPriceSwapped()));
        String capture = dealPatch.getValue().getOriginalSkuPrices();
        assertTrue(capture.contains("\"14\"") && capture.contains("\"15\""), capture);
        assertTrue(capture.contains("12.50") && capture.contains("20.00"), capture);
    }

    @Test
    public void cjUnwindRestoresSkusAndRepromotesFromSnapshot() {
        LitemallSeckill deal = new LitemallSeckill();
        deal.setId(7);
        deal.setGoodsId(42);
        deal.setPrice(new BigDecimal("41.30"));
        deal.setOriginalRetailPrice(new BigDecimal("59.00"));
        deal.setOriginalSkuPrices("{\"14\":{\"o\":59.00,\"s\":41.30}}");
        when(seckillService.queryDueForExpiry()).thenReturn(List.of(deal));

        LitemallGoods goods = new LitemallGoods();
        goods.setId(42);
        goods.setSource("cj");
        goods.setCjPid("pid-1");
        goods.setRetailPrice(new BigDecimal("41.30")); // still the deal price → restore applies
        when(goodsService.findById(42)).thenReturn(goods);
        when(productService.findById(14)).thenReturn(sku(14, "41.30"));

        LitemallCjProduct snapshot = new LitemallCjProduct();
        snapshot.setPid("pid-1");
        when(cjProductService.findByPid("pid-1")).thenReturn(snapshot);

        task.tick();

        ArgumentCaptor<LitemallGoodsProduct> skuPatch =
                ArgumentCaptor.forClass(LitemallGoodsProduct.class);
        verify(productService).updateById(skuPatch.capture());
        assertEquals(new BigDecimal("59.00"), skuPatch.getValue().getPrice());
        verify(cjPromotionService).promote(snapshot);
    }

    @Test
    public void adminEditedSkuSurvivesUnwindAndLocalGoodsSkipRepromote() {
        LitemallSeckill deal = new LitemallSeckill();
        deal.setId(8);
        deal.setGoodsId(43);
        deal.setPrice(new BigDecimal("10.00"));
        deal.setOriginalRetailPrice(new BigDecimal("12.50"));
        deal.setOriginalSkuPrices("{\"20\":{\"o\":12.50,\"s\":10.00}}");
        when(seckillService.queryDueForExpiry()).thenReturn(List.of(deal));

        LitemallGoods goods = new LitemallGoods();
        goods.setId(43);
        goods.setSource("local");
        goods.setRetailPrice(new BigDecimal("11.00")); // admin changed it mid-deal
        when(goodsService.findById(43)).thenReturn(goods);
        when(productService.findById(20)).thenReturn(sku(20, "9.00")); // admin changed it too

        task.tick();

        verify(productService, never()).updateById(org.mockito.ArgumentMatchers.any());
        verify(goodsService, never()).updateById(org.mockito.ArgumentMatchers.any());
        verify(cjPromotionService, never()).promote(org.mockito.ArgumentMatchers.any());
    }
}
