package org.linlinjava.litemall.order.application.internal.cj;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.order.application.util.exception.order.LitemallOrderServiceException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * {@link OrderSourceResolver}: an order is 'cj' only when EVERY line is CJ-sourced,
 * 'local' otherwise (including unknown/pre-V23 rows), and a mixed cart is rejected —
 * one order can never ship through two fulfillment channels.
 */
@ExtendWith(MockitoExtension.class)
class OrderSourceResolverTest {

    @Mock
    private LitemallGoodsService goodsService;

    @InjectMocks
    private OrderSourceResolver resolver;

    private LitemallCartAggregate cartLine(int goodsId) {
        LitemallCartAggregate line = new LitemallCartAggregate();
        line.setGoodsId(new LitemallGoodsId(goodsId));
        return line;
    }

    private LitemallGoods goods(String source) {
        LitemallGoods goods = new LitemallGoods();
        goods.setSource(source);
        return goods;
    }

    @Test
    void allCjLines_resolveToCj() {
        when(goodsService.findById(1)).thenReturn(goods("cj"));
        when(goodsService.findById(2)).thenReturn(goods("cj"));

        assertEquals(LitemallOrderAggregate.SOURCE_CJ,
                resolver.resolve(List.of(cartLine(1), cartLine(2))));
    }

    @Test
    void allLocalLines_resolveToLocal() {
        when(goodsService.findById(1)).thenReturn(goods("local"));

        assertEquals(LitemallOrderAggregate.SOURCE_LOCAL,
                resolver.resolve(List.of(cartLine(1))));
    }

    @Test
    void unknownOrPreV23Goods_readAsLocal() {
        when(goodsService.findById(1)).thenReturn(goods(null));
        when(goodsService.findById(2)).thenReturn(null);

        assertEquals(LitemallOrderAggregate.SOURCE_LOCAL,
                resolver.resolve(List.of(cartLine(1), cartLine(2))));
    }

    @Test
    void mixedCart_isRejected() {
        when(goodsService.findById(1)).thenReturn(goods("cj"));
        when(goodsService.findById(2)).thenReturn(goods("local"));

        assertThrows(LitemallOrderServiceException.class,
                () -> resolver.resolve(List.of(cartLine(1), cartLine(2))));
    }
}
