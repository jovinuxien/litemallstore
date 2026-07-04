package org.linlinjava.litemall.order.domain.service.order;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallGoodsFacade;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that order placement reads the authoritative price from goods-management
 * rather than trusting the cart row: {@code validateProductStock} stamps the
 * goods-service product price onto each cart line it validates.
 */
class LitemallOrderDomainServicePriceTest {

    @Test
    void validateProductStock_stampsAuthoritativePriceFromGoodsService() {
        LitemallGoodsFacade facade = mock(LitemallGoodsFacade.class);

        LitemallCartAggregate cart = new LitemallCartAggregate();
        cart.setGoodsId(new org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId(7));
        cart.setProductId(new LitemallGoodsProductId(42));
        cart.setNumber(2);
        cart.setPrice(new LitemallMoney(new BigDecimal("5.00"))); // stale / tampered cart price

        LitemallGoodsProductAggregate product = new LitemallGoodsProductAggregate();
        product.setGoodsProductId(new LitemallGoodsProductId(42));
        product.setNumber(10);
        product.setPrice(new LitemallMoney(new BigDecimal("12.50"))); // authoritative price
        // The facade reads variants per goods; the domain service picks the cart line's one.
        when(facade.getProductsByGoods(any())).thenReturn(List.of(product));

        new LitemallOrderDomainService().validateProductStock(List.of(cart), facade);

        // The cart line now carries the server price, not the 5.00 it arrived with.
        assertEquals(0, new BigDecimal("12.50").compareTo(cart.getPrice().getAmount()));
    }
}
