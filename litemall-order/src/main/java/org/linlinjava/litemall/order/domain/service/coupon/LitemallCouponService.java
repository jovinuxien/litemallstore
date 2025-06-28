package org.linlinjava.litemall.order.domain.service.coupon;

import org.linlinjava.litemall.db.util.CouponConstant;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCouponAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCouponUserAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCouponRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCouponUserRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCouponUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallCouponUserStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.GoodsServiceFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class LitemallCouponService {

    @Autowired
    private LitemallCouponRepository couponRepository;
    @Autowired
    private LitemallCouponUserRepository couponUserRepository;
    /*@Autowired
    private LitemallGoodsRepository goodsRepository;*/
    @Autowired
    private GoodsServiceFeignClient goodsServiceFeignClient;



    public LitemallCouponAggregate checkCoupon(LitemallUserId userId, LitemallCouponId couponId, LitemallCouponUserId userCouponId, LitemallMoney checkedGoodsPrice, List<LitemallCartAggregate> cartList){

        LitemallCouponAggregate coupon = couponRepository.findById(couponId).get();

        if (coupon == null || coupon.isDeleted()) {
            return null;
        }

        LitemallCouponUserAggregate couponUser = couponUserRepository.findById(userCouponId);

        if (couponUser == null) {
            couponUser = couponUserRepository.findOne(couponId, userId);
        } else if (!couponId.equals(couponUser.getCouponId())) {
            return null;
        }

        if (couponUser == null) {
            return null;
        }

        // Check if it is overdue
        Short timeType = coupon.getType().getValue();
        Short days = coupon.getDays().shortValue();
        LocalDateTime now = LocalDateTime.now();
        if (timeType.equals(CouponConstant.TIME_TYPE_TIME)) {
            if (now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())) {
                return null;
            }
        }
        else if(timeType.equals(CouponConstant.TIME_TYPE_DAYS)) {
            LocalDateTime expired = couponUser.getAddTime().plusDays(days);
            if (now.isAfter(expired)) {
                return null;
            }
        }
        else {
            return null;
        }

        // Check whether the product meets the
        Map<Integer, List<LitemallCartAggregate>> cartMap = new HashMap<>();

        //Items or categories where coupons can be used
        List<Integer> goodsValueList = new ArrayList<>(Arrays.asList(coupon.getGoodsValue()));
        Short goodType = coupon.getGoodsType();

        if (goodType.equals(CouponConstant.GOODS_TYPE_CATEGORY) ||
                goodType.equals((CouponConstant.GOODS_TYPE_ARRAY))) {
            for (LitemallCartAggregate cart : cartList) {
                Integer key = goodType.equals(CouponConstant.GOODS_TYPE_ARRAY) ? cart.getGoodsId().getId() :
                        goodsServiceFeignClient.getGoodsAggregate(cart.getGoodsId().getId()).getData().getCategoryId().getId();

                List<LitemallCartAggregate> carts = cartMap.get(key);
                if (carts == null) {
                    carts = new LinkedList<>();
                }
                carts.add(cart);
                cartMap.put(key, carts);
            }
            //Items or categories in the shopping cart that can use the coupon
            goodsValueList.retainAll(cartMap.keySet());
            //The total price of the items that can be used with the coupon
            BigDecimal total = new BigDecimal(0);

            for (Integer goodsId : goodsValueList) {
                List<LitemallCartAggregate> carts = cartMap.get(goodsId);
                for (LitemallCartAggregate cart : carts) {
                    total = total.add(cart.getPrice().getAmount().multiply(new BigDecimal(cart.getNumber())));
                }
            }
            //Whether the coupon discount amount has been reached
            if (total.compareTo(coupon.getMin()) == -1) {
                return null;
            }
        }

        // Check order status
        Short status = coupon.getStatus().getValue();
        if (!status.equals(CouponConstant.STATUS_NORMAL)) {
            return null;
        }
        // Check whether the minimum consumption is met
        if (checkedGoodsPrice.getAmount().compareTo(coupon.getMin()) == -1) {
            return null;
        }
        return coupon;
    }

    public void couponUserUpdateUsage(LitemallCouponUserId couponUserId, LitemallOrderId orderId){
        LitemallCouponUserAggregate couponUserAggregate = couponUserRepository.findById(couponUserId);
        couponUserAggregate.setStatus(LitemallCouponUserStatus.USED);
        couponUserAggregate.setUsedTime(LocalDateTime.now());
        couponUserAggregate.setOrderId(orderId);
        couponUserRepository.updateCouponUser(couponUserAggregate);
    }

    public LitemallCouponUserAggregate getUserCouponById(LitemallCouponUserId couponUserId){
        return couponUserRepository.findById(couponUserId);
    }

    public int  updateCouponUser(LitemallCouponUserAggregate couponUserAggregate) {
        return couponUserRepository.updateCouponUser(couponUserAggregate);
    }
}
