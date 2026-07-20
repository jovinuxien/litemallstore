package org.linlinjava.litemall.order.domain.service.order;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.order.application.util.exception.product.LitemallInsufficientStockException;
import org.linlinjava.litemall.order.application.util.exception.product.LitemallPriceChangedException;
import org.linlinjava.litemall.order.application.util.exception.product.LitemallProductNotFoundException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponRulesAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallGoodsFacade;

import java.math.BigDecimal;
import java.util.List;

public class LitemallOrderDomainService {


    public BigDecimal priceCalculation(List<LitemallCartAggregate> checkedGoodsList, LitemallGrouponRulesAggregate grouponRules, LitemallMoney grouponPrice){
        BigDecimal checkedGoodsPrice = new BigDecimal(0);
        for (LitemallCartAggregate checkedGoods : checkedGoodsList) {
            //  Only when the product ID meets the group purchase specifications will the group purchase discount be available
            if (grouponRules != null && grouponRules.getGoodsId().equals(checkedGoods.getGoodsId())) {
                checkedGoodsPrice = checkedGoodsPrice.add(checkedGoods.getPrice().getAmount().subtract(grouponPrice.getAmount()).multiply(new BigDecimal(checkedGoods.getNumber())));
            } else {
                checkedGoodsPrice = checkedGoodsPrice.add(checkedGoods.getPrice().getAmount().multiply(new BigDecimal(checkedGoods.getNumber())));
            }
        }
        return checkedGoodsPrice;
    }

    /**
     * Authoritative stock + price gate for a checkout, run against goods-management
     * immediately before {@link #priceCalculation}.
     *
     * <p>Despite the name this is the money path's guard, not just a stock check: it is
     * what makes the order total independent of the cart row, and therefore of the
     * client. Anything that weakens it (a null price falling through, a mismatch being
     * silently absorbed) puts the client back in charge of the price — so both are
     * hard failures. Callers must invoke it BEFORE pricing; today
     * {@code LitemallOrderServiceImpl.placeOrder} is the only caller.
     *
     * @param checkedCartItems the checked cart lines; priced in place on success
     * @throws LitemallInsufficientStockException variant missing, or not enough stock
     * @throws LitemallProductNotFoundException   variant carries no price
     * @throws LitemallPriceChangedException      cart price disagrees with the catalog
     */
    public void validateProductStock(List<LitemallCartAggregate> checkedCartItems, LitemallGoodsFacade goodsFacade) {
        // Sale-status gate. A cart line can predate a good being taken off sale
        // (e.g. the legacy non-CJ catalog, deactivated 2026-07-20 because no
        // inventory backs it), and cart-add's own gate cannot help a row that is
        // already persisted — so submit re-checks against the catalog.
        java.util.Map<org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId,
                org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate> goodsById =
                goodsFacade.batchGetGoods(checkedCartItems.stream()
                        .map(c -> c.getGoodsId().getId())
                        .collect(java.util.stream.Collectors.toSet()));

        for(LitemallCartAggregate cartItem : checkedCartItems){

            org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate goods =
                    goodsById.get(cartItem.getGoodsId());
            if (goods == null || !goods.isOnSale()) {
                throw new LitemallProductNotFoundException(
                        "\"" + cartItem.getGoodsName() + "\" is no longer available for sale"
                                + " — remove it from your cart and place the order again.");
            }

            // goods-management exposes products per goods (not per product id), so
            // fetch the goods' variants and pick the one this cart line references.
            LitemallGoodsProductAggregate goodsProduct = goodsFacade.getProductsByGoods(cartItem.getGoodsId()).stream()
                    .filter(p -> p.getGoodsProductId().equals(cartItem.getProductId()))
                    .findFirst()
                    .orElseThrow(LitemallInsufficientStockException::new);

            if(goodsProduct.getNumber() < cartItem.getNumber()){
                throw new LitemallInsufficientStockException();
            }

            // Stamp the authoritative current price from goods-management onto the
            // cart line, so downstream price calculation and the persisted
            // order-goods price come from the source of truth rather than a stale
            // or tampered cart row. This is the last line of defence for the order
            // total, so a variant we cannot price must fail the checkout: falling
            // through would charge whatever the cart row happens to carry.
            if (goodsProduct.getPrice() == null) {
                throw new LitemallProductNotFoundException(
                        "goods-management returned no price for product " + cartItem.getProductId().getId()
                                + " — it cannot be ordered right now");
            }

            // The cart line is priced from the catalog at add time, so a disagreement
            // here means the price moved while the cart sat (or the row predates
            // server-authoritative cart-add). Recomputing silently would charge an
            // amount the customer never saw — surface it and let them re-check out.
            if (cartItem.getPrice() == null || !cartItem.getPrice().equals(goodsProduct.getPrice())) {
                throw new LitemallPriceChangedException(
                        "The price of \"" + cartItem.getGoodsName() + "\" changed to "
                                + goodsProduct.getPrice().getAmount()
                                + " — review your cart and place the order again.");
            }

            cartItem.setPrice(goodsProduct.getPrice());
        }
    }

}
