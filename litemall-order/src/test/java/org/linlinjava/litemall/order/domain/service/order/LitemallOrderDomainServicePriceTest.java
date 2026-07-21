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
 * rather than trusting the cart row. Since Wave 7 a cart/catalog price disagreement is a
 * HARD failure ({@code LitemallPriceChangedException}) — recomputing silently would charge
 * an amount the customer never saw — and an agreeing line passes with the catalog price.
 */
class LitemallOrderDomainServicePriceTest {

    @Test
    void validateProductStock_agreeingCartPrice_passesWithCatalogPrice() {
        LitemallGoodsFacade facade = mock(LitemallGoodsFacade.class);

        LitemallCartAggregate cart = new LitemallCartAggregate();
        cart.setGoodsId(new org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId(7));
        cart.setProductId(new LitemallGoodsProductId(42));
        cart.setNumber(2);
        cart.setPrice(new LitemallMoney(new BigDecimal("12.50"))); // agrees with the catalog

        LitemallGoodsProductAggregate product = new LitemallGoodsProductAggregate();
        product.setGoodsProductId(new LitemallGoodsProductId(42));
        product.setNumber(10);
        product.setPrice(new LitemallMoney(new BigDecimal("12.50"))); // authoritative price
        // The facade reads variants per goods; the domain service picks the cart line's one.
        when(facade.getProductsByGoods(any())).thenReturn(List.of(product));
        // Sale-status gate (2026-07-20): validateProductStock first re-checks the goods is
        // still on sale via batchGetGoods — serve an on-sale goods row for the cart line.
        org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate goods =
                new org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate();
        goods.setGoodsId(new org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId(7));
        goods.setOnSale(true);
        when(facade.batchGetGoods(any())).thenReturn(java.util.Map.of(
                new org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId(7), goods));

        new LitemallOrderDomainService().validateProductStock(List.of(cart), facade);

        // The cart line carries the (agreeing) catalog price.
        assertEquals(0, new BigDecimal("12.50").compareTo(cart.getPrice().getAmount()));

        // A stale/tampered cart price is a hard failure, never silently re-stamped —
        // charging a total the customer did not see is the failure mode this gate blocks.
        cart.setPrice(new LitemallMoney(new BigDecimal("5.00")));
        org.junit.jupiter.api.Assertions.assertThrows(
                org.linlinjava.litemall.order.application.util.exception.product.LitemallPriceChangedException.class,
                () -> new LitemallOrderDomainService().validateProductStock(List.of(cart), facade));
    }
}
