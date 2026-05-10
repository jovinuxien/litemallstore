package org.linlinjava.litemall.order.domain.model.domainservices.coupon;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCouponAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCouponUserAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponValidationResult;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons.LitemallCouponGoodsType;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons.LitemallCouponStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons.LitemallCouponTimeType;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons.LitemallCouponUserStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * @Desc: We decided to focus on domain service here because
 * the validation rules touches two values objects: Coupon and order.
 */

@Service
public class LitemallCouponDomainService {

    /**
     *
     * @param coupon aggregate
     * @return
     */
    public LitemallCouponValidationResult validateCouponStatus(LitemallCouponAggregate coupon) {
        if(coupon.getStatus().equals(LitemallCouponStatus.NORMAL)){
            return LitemallCouponValidationResult.invalidStatus();
        }
        return LitemallCouponValidationResult.valid(null, "The status is valid");
    }

    /**
     *
     * @param coupon
     * @param couponUser
     * @return
     */
    public LitemallCouponValidationResult validateCouponUser(LitemallCouponAggregate coupon, LitemallCouponUserAggregate couponUser) {

        // Check if couponUser has been used
        if(!couponUser.getStatus().equals(LitemallCouponUserStatus.USABLE)){
            return LitemallCouponValidationResult.invalidUserStatus("Coupon is already used or expired.");
        }

        // Check the expiration based on coupon type
        LocalDateTime now = LocalDateTime.now();
        if(coupon.getTimeType().equals(LitemallCouponTimeType.TIME_TYPE_TIME)){
            if(now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())){
                return LitemallCouponValidationResult.expired("Coupon is not within the valid range");
            }
        }else if (coupon.getTimeType().equals(LitemallCouponTimeType.TIME_TYPE_DAYS)){
            LocalDateTime expiredDate = couponUser.getAddTime().plusDays(coupon.getDays());
            if(now.isAfter(expiredDate)){
                return LitemallCouponValidationResult.expired("Coupon has expired based on days validity");
            }
        }else {
            return LitemallCouponValidationResult.invalid("Invalid coupon time type. Please check your data.");
        }
        return LitemallCouponValidationResult.valid(null, "Coupon validation is ok");
    }

    /**
     *
     * @param coupon
     * @param cartList
     * @param goodsMap
     * @return
     */
    public LitemallCouponValidationResult validateGoodsApplicability(LitemallCouponAggregate coupon,
                                                                      List<LitemallCartAggregate> cartList,
                                                                     Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap) {

        BigDecimal cartTotalAmount = calculateCartTotal(cartList).getAmount();
        // Check the minimum amount requirement
        if(cartTotalAmount.compareTo(coupon.getMinPrice()) < 0){
            return LitemallCouponValidationResult.insufficientMinAmount(
                    "Cart total " + cartTotalAmount + " is less than minimum required " + coupon.getMinPrice());
        }

        // For category or specific goods coupons, validate applicability
        if (!coupon.getGoodsType().equals(LitemallCouponGoodsType.GOODS_TYPE_ALL)) {
            LitemallMoney applicableGoodsTotal = calculateApplicableGoodsTotal(coupon, cartList, goodsMap);
            if (applicableGoodsTotal.getAmount().compareTo(coupon.getMinPrice()) < 0) {
                return LitemallCouponValidationResult.insufficientApplicableGoodsAmount(
                        "Applicable goods total " + applicableGoodsTotal + " is less than minimum required " + coupon.getMinPrice()
                );
            }
        }

        // For restricted coupons, validate specific goods
        /*if (coupon.hasGoodsRestrictions()) {
            LitemallMoney applicableTotal = calculateApplicableGoodsTotal(coupon, cartList, goodsMap);
            if (applicableTotal.getAmount().compareTo(coupon.getMinPrice()) < 0) {
                return LitemallCouponValidationResult.insufficientApplicableGoodsAmount(
                        "Applicable goods total " + applicableTotal + " is less than minimum required " + coupon.getMinPrice());
            }
        }*/
        return LitemallCouponValidationResult.valid(null, "Goods applicability OK");
    }


    /**
     *
     * @param coupon
     * @param cartList
     * @param goodsAggregate
     * @return
     */
    public LitemallMoney calculateCouponDiscount(LitemallCouponAggregate coupon, List<LitemallCartAggregate> cartList, Map<LitemallGoodsId, LitemallGoodsAggregate> goodsAggregate) {
        // Simple implementation - adjust based on your discount calculation logic
        BigDecimal discountAmount = coupon.getDiscount();
        // For more complex calculations, you might need the applicable goods total
        LitemallMoney applicableTotal = calculateApplicableGoodsTotal(coupon, cartList, goodsAggregate);
        // Apply discount logic based on applicable total
        discountAmount = applicableTotal.getAmount().multiply(coupon.getDiscount().divide(BigDecimal.valueOf(100)));

        return new LitemallMoney(discountAmount);
    }


    /**
     *
     * @param coupon
     * @param cartList
     * @param goodsMap
     * @return
     */
    private LitemallMoney calculateApplicableGoodsTotal(LitemallCouponAggregate coupon,
                                                     List<LitemallCartAggregate> cartList, Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap) {

        Set<Integer> applicableGoodsValues = new HashSet<>(Arrays.asList(coupon.getGoodsValue()));
        BigDecimal total = BigDecimal.ZERO;

        for (LitemallCartAggregate cart : cartList) {
            //No external calls to external services here, we just assume the existence of goodsMap
            Integer goodsKey = getGoodsKeyForCoupon(coupon, cart, goodsMap.get(cart.getGoodsId()));
            if (applicableGoodsValues.contains(goodsKey)) {
                total = total.add(cart.getPrice().getAmount().multiply(BigDecimal.valueOf(cart.getNumber())));
            }
        }
        return new LitemallMoney(total);
    }




    /**
     *
     * @param coupon
     * @param cart
     * @param goods
     * @return
     */
    public Integer  getGoodsKeyForCoupon(LitemallCouponAggregate coupon, LitemallCartAggregate cart, LitemallGoodsAggregate goods) {

        if(coupon.getGoodsType().equals(LitemallCouponGoodsType.GOODS_TYPE_ARRAY)) {
            return cart.getGoodsId().getId();
        } else if(coupon.getGoodsType().equals(LitemallCouponGoodsType.GOODS_TYPE_CATEGORY)){
            //return goodsServiceFeignClient.getGoodsAggregate(cart.getGoodsId().getId()).getData().getCategoryId().getId();
            goods.setGoodsId(cart.getGoodsId());// On the client side of this method with we fetch goods aggregate through FeignClient
            return goods.getCategoryId().getId();
        }
        return null;
    }


    LitemallMoney calculateCartTotal(List<LitemallCartAggregate> cartList) {
        BigDecimal total = BigDecimal.ZERO;
        for (LitemallCartAggregate cart : cartList) {
            total = total.add(cart.getPrice().getAmount().multiply(BigDecimal.valueOf(cart.getNumber())));
        }
        return new LitemallMoney(total);
    }



}
