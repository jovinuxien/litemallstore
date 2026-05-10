package org.linlinjava.litemall.order.domain.model.domainservices.order;

import com.google.protobuf.ServiceException;
import org.linlinjava.litemall.order.application.util.exception.product.LitemallInsufficientStockException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallGrouponRulesAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.FeignResponseHandler;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.GoodsServiceFeignClient;

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
    public void validateProductStock(List<LitemallCartAggregate> checkedCartItems, GoodsServiceFeignClient goodsServiceFeignClient) throws ServiceException {
        for(LitemallCartAggregate cartItem : checkedCartItems){

            LitemallGoodsProductAggregate goodsProduct = FeignResponseHandler.handleResponse(goodsServiceFeignClient.getGoodsProductAggregate(cartItem.getProductId().getId()), "Get goods Product");

            System.out.println("the goodsProduct is: " + goodsProduct);

            if(goodsProduct.getNumber() < cartItem.getNumber()){
                throw new LitemallInsufficientStockException();
            }
        }
    }

}
