package org.linlinjava.litemall.order.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.db.util.CouponConstant;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCouponAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCouponUserAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.domainservices.coupon.LitemallCouponDomainService;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCouponRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCouponUserRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCouponUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.CouponValidationContext;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons.LitemallCouponUserStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.GoodsServiceFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LitemallCouponServiceLayer {

    @Autowired
    private LitemallCouponRepository couponRepository;
    @Autowired
    private LitemallCouponUserRepository couponUserRepository;
    @Autowired
    private GoodsServiceFeignClient goodsServiceFeignClient;
    @Autowired
    private LitemallCouponDomainService couponService;



    public LitemallCouponAggregate checkCoupon(LitemallUserId userId, LitemallCouponId couponId, LitemallCouponUserId userCouponId, LitemallMoney checkedGoodsPrice, List<LitemallCartAggregate> cartList){

        LitemallCouponAggregate coupon = couponRepository.findById(couponId).get();
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
        couponService.validateCouponUser(coupon, couponUser);


        // Check whether the product meets the
        Map<Integer, List<LitemallCartAggregate>> cartMap = new HashMap<>();
        //Items or categories where coupons can be used
        List<Integer> goodsValueList = new ArrayList<>(Arrays.asList(coupon.getGoodsValue()));
        Short goodType = coupon.getGoodsType();

        if (goodType.equals(CouponConstant.GOODS_TYPE_CATEGORY) || goodType.equals((CouponConstant.GOODS_TYPE_ARRAY))) {
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


    private CouponValidationContext loadValidationContext(
            LitemallUserId userId,
            LitemallCouponId couponId,
            List<LitemallCartAggregate> cartList){

        try {
            // Load coupon and user coupon in parallel
            CompletableFuture<LitemallCouponAggregate> couponFuture =
                    CompletableFuture.supplyAsync(() -> couponRepository.findById(couponId).orElseThrow(() -> new NoSuchElementException("Coupon not found")));

            CompletableFuture<LitemallCouponUserAggregate> couponUserFuture =
                    CompletableFuture.supplyAsync(() -> couponUserRepository.findByUserAndCoupon(userId, couponId));

            // Batch load all required goods data
            Set<LitemallGoodsId> goodsIds = cartList.stream()
                    .map(LitemallCartAggregate::getGoodsId)
                    .collect(Collectors.toSet());

            Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap =
                    goodsServiceFeignClient.batchGetGoodsAggregates(goodsIds);

            // Use the factory method
            return org.linlinjava.litemall.order.domain.model.valueobjects.coupon.CouponValidationContext.create(
                    couponFuture.join(),
                    couponUserFuture.join(),
                    goodsMap
            );

        } catch (Exception e) {
            // Handle exceptions gracefully
            log.error("Failed to load coupon validation context for user {} and coupon {}",
                    userId, couponId, e);
            return org.linlinjava.litemall.order.domain.model.valueobjects.coupon.CouponValidationContext.couponNotFound();
        }
    }
}
