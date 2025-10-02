package org.linlinjava.litemall.order.domain.model.domainservices.coupon;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCouponAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCouponUserAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons.LitemallCouponGoodsType;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons.LitemallCouponStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons.LitemallCouponTimeType;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons.LitemallCouponUserStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @Desc: We decided to focus on domain service here because
 * the validation rules touches two values objects: Coupon and order.
 */
public class LitemallCouponServiceChecker {



    public LitemallCouponValidationResult validateCouponForUser(LitemallCouponAggregate coupon, LitemallCartAggregate cart) {

        if(coupon.getStatus() == LitemallCouponStatus.EXPIRED) {
            return LitemallCouponValidationResult.expired();
        }

        if(cart.get)
    }


    /**
     *
     * @param coupon aggregate
     * @return
     */
    private LitemallCouponValidationResult validateCouponStatus(LitemallCouponAggregate coupon) {
        if(coupon.getStatus().equals(LitemallCouponStatus.NORMAL)){
            return LitemallCouponValidationResult.invalidStatus();
        }
        return LitemallCouponValidationResult.valid(null);
    }

    /**
     *
     * @param coupon
     * @param couponUser
     * @return
     */
    private LitemallCouponValidationResult validateCouponUser(LitemallCouponAggregate coupon, LitemallCouponUserAggregate couponUser) {

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


    private LitemallCouponValidationResult validateGoodsApplicability(LitemallCouponAggregate coupon,
                                                                      List<LitemallCartAggregate> cartList, LitemallMoney checkedGoodsPrice) {}


    private LitemallMoney calculateCouponDiscount(LitemallCouponAggregate coupon, List<LitemallCartAggregate> cartList) {
        // Simple implementation - adjust based on your discount calculation logic
        BigDecimal discountAmount = coupon.getDiscount();
        // For more complex calculations, you might need the applicable goods total
        BigDecimal applicableTotal = calculateApplicableGoodsTotal(coupon, cartList);
        // Apply discount logic based on applicable total
        discountAmount = applicableTotal.multiply(coupon.getDiscount().divide(BigDecimal.valueOf(100)));

        return new LitemallMoney(discountAmount);
    }

    private BigDecimal calculateApplicableGoodsTotal(LitemallCouponAggregate coupon, List<LitemallCartAggregate> cartList, LitemallGoodsAggregate goods) {
        Set<Integer> applicableGoodsValues = new HashSet<>(Arrays.asList(coupon.getGoodsValue()));
        BigDecimal applicableGoodsTotal = BigDecimal.ZERO;

        for(LitemallCartAggregate cart : cartList) {
            Integer key = geGoodsKeyForCoupon(coupon, cart, goods);
            if(applicableGoodsValues.contains(key)) {
                applicableGoodsTotal = applicableGoodsTotal.add(cart.getPrice().getAmount().multiply(BigDecimal.valueOf(cart.getNumber())));
            }
        }

    }


    private Integer  geGoodsKeyForCoupon(LitemallCouponAggregate coupon, LitemallCartAggregate cart, LitemallGoodsAggregate goods) {
        if(coupon.getGoodsType().equals(LitemallCouponGoodsType.GOODS_TYPE_ARRAY)) {
            return cart.getGoodsId().getId();
        } else if(coupon.getGoodsType().equals(LitemallCouponGoodsType.GOODS_TYPE_CATEGORY)){
            //return goodsServiceFeignClient.getGoodsAggregate(cart.getGoodsId().getId()).getData().getCategoryId().getId();
            goods.setGoodsId(cart.getGoodsId());// On the client side of this method with we fetch goods aggregate through FeignClient
            return goods.getCategoryId().getId();
        }
        return null;
    }
}
