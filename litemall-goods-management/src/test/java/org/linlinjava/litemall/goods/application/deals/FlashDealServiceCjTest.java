package org.linlinjava.litemall.goods.application.deals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallSeckill;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.db.service.LitemallSeckillService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave-12 CJ unpark: the blanket 652 refusal is gone — CJ deals are floored at the REAL
 * captured cost (650 names the floor); 652 is narrowed to "no captured cost" (0.00 = the
 * V2 default = not captured). Local goods behave exactly as before.
 */
public class FlashDealServiceCjTest {

    private LitemallSeckillService seckillService;
    private LitemallGoodsService goodsService;
    private FlashDealService service;

    private final LocalDateTime start = LocalDateTime.now().plusHours(1);
    private final LocalDateTime stop = LocalDateTime.now().plusDays(1);

    @BeforeEach
    public void setUp() {
        seckillService = mock(LitemallSeckillService.class);
        goodsService = mock(LitemallGoodsService.class);
        service = new FlashDealService(seckillService, goodsService);
    }

    private LitemallGoods cjGoods(String cost, String retail) {
        LitemallGoods goods = new LitemallGoods();
        goods.setId(42);
        goods.setName("CJ Goods");
        goods.setSource("cj");
        goods.setCost(cost == null ? null : new BigDecimal(cost));
        goods.setRetailPrice(new BigDecimal(retail));
        when(goodsService.findById(42)).thenReturn(goods);
        return goods;
    }

    @Test
    public void cjWithoutCapturedCostIsRefused652() {
        cjGoods(null, "12.50");
        FlashDealService.Result result =
                service.create(42, new BigDecimal("9.99"), start, stop, 10);
        assertEquals(Integer.valueOf(652), result.errno());
        assertTrue(result.error().contains("cost basis unknown"), result.error());
    }

    @Test
    public void zeroCostCountsAsNotCaptured() {
        cjGoods("0.00", "12.50");
        assertEquals(Integer.valueOf(652),
                service.create(42, new BigDecimal("9.99"), start, stop, 10).errno());
    }

    @Test
    public void priceBelowCostFloorIs650NamingTheFloor() {
        cjGoods("10.00", "12.50");
        FlashDealService.Result result =
                service.create(42, new BigDecimal("9.99"), start, stop, 10);
        assertEquals(Integer.valueOf(650), result.errno());
        assertTrue(result.error().contains("10.00"), "floor must be named: " + result.error());
    }

    @Test
    public void priceAtOrAboveCostCreatesTheDeal() {
        cjGoods("10.00", "12.50");
        when(seckillService.hasOverlapping(any(), any(), any(), any())).thenReturn(false);

        FlashDealService.Result result =
                service.create(42, new BigDecimal("10.50"), start, stop, 10);

        assertNull(result.error(), "expected success, got: " + result.error());
        verify(seckillService).add(any(LitemallSeckill.class));
        assertEquals(Integer.valueOf(42), result.deal().getGoodsId());
    }

    @Test
    public void updateEnforcesTheCjFloorToo() {
        cjGoods("10.00", "12.50");
        LitemallSeckill deal = new LitemallSeckill();
        deal.setId(7);
        deal.setGoodsId(42);
        deal.setPrice(new BigDecimal("11.00"));
        deal.setStartTime(start);
        deal.setStopTime(stop);
        deal.setPriceSwapped(false);
        when(seckillService.findById(7)).thenReturn(deal);

        FlashDealService.Result result =
                service.update(7, new BigDecimal("9.50"), null, null, null, null);

        assertEquals(Integer.valueOf(650), result.errno());
        assertTrue(result.error().contains("floor"), result.error());
    }
}
