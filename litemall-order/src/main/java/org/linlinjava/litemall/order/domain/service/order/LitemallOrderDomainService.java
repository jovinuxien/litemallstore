package org.linlinjava.litemall.order.domain.service.order;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.order.application.util.exception.product.LitemallInsufficientStockException;
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
     *
     * @param checkedCartItems
     */
    public void validateProductStock(List<LitemallCartAggregate> checkedCartItems, LitemallGoodsFacade goodsFacade) {
        for(LitemallCartAggregate cartItem : checkedCartItems){

            LitemallGoodsProductAggregate goodsProduct = goodsFacade.getGoodsProduct(cartItem.getProductId());

            if(goodsProduct.getNumber() < cartItem.getNumber()){
                throw new LitemallInsufficientStockException();
            }

            // Stamp the authoritative current price from goods-management onto the
            // cart line, so downstream price calculation and the persisted
            // order-goods price come from the source of truth rather than a stale
            // or tampered cart row.
            if(goodsProduct.getPrice() != null){
                cartItem.setPrice(goodsProduct.getPrice());
            }
        }
    }

}
