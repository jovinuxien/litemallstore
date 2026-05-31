package org.linlinjava.litemall.order.application.internal;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCouponAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCouponUserAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
import org.linlinjava.litemall.order.domain.service.coupon.LitemallCouponDomainService;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCouponRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCouponUserRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCouponUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.CouponValidationContext;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponValidationResult;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.coupons.LitemallCouponUserStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.LitemallGoodsFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    private LitemallGoodsFacade goodsFacade;
    @Autowired
    private LitemallCouponDomainService couponService;



    public LitemallCouponValidationResult validateCouponApplication(
            LitemallUserId userId,
            LitemallCouponId couponId,
            List<LitemallCartAggregate> cartList) {

        // Step 1: Load all required data efficiently
        CouponValidationContext context = loadValidationContext(userId, couponId, cartList);

        // Step 2: Validate coupon status and user eligibility
        LitemallCouponValidationResult statusValidation = couponService.validateCouponStatus(context.getCoupon());
        if (!statusValidation.isValid()) return statusValidation;

        // Step 3: Validate user coupon and eligibility
        LitemallCouponValidationResult userCouponValidation = couponService.validateCouponUser(context.getCoupon(), context.getCouponUser());
        if(!userCouponValidation.isValid()) return userCouponValidation;

        // Step 4: Validate goods applicability (Method 2 approach)
        LitemallCouponValidationResult goodsValidation = couponService.validateGoodsApplicability(context.getCoupon(), cartList, context.getGoodsMap());
        if (!goodsValidation.isValid()) return goodsValidation;

        // Step 5: Calculate and return final discount
        LitemallMoney discount = couponService.calculateCouponDiscount(context.getCoupon(), cartList, context.getGoodsMap());
        return LitemallCouponValidationResult.valid(discount, "Coupon is applicable");
    }



    public void couponUserUpdateUsage(LitemallCouponUserId couponUserId, LitemallOrderId orderId){
        LitemallCouponUserAggregate couponUserAggregate = couponUserRepository.findById(couponUserId).orElseThrow(() -> new NoSuchElementException("User Coupon Aggregate could not be found"));
        couponUserAggregate.setStatus(LitemallCouponUserStatus.USED);
        couponUserAggregate.setUsedTime(LocalDateTime.now());
        couponUserAggregate.setOrderId(orderId);
        couponUserRepository.updateCouponUser(couponUserAggregate);
    }

    public LitemallCouponUserAggregate getUserCouponById(LitemallCouponUserId couponUserId){
        return couponUserRepository.findById(couponUserId).orElseThrow(() -> new NoSuchElementException("User Coupon Aggregate could not be found"));
    }

    public int  updateCouponUser(LitemallCouponUserAggregate couponUserAggregate) {
        return couponUserRepository.updateCouponUser(couponUserAggregate);
    }

    /**
     *
     * @param couponId
     * @return
     */
    public LitemallCouponAggregate getCouponAggregate(LitemallCouponId couponId){
        return couponRepository.findById(couponId).orElseThrow(() -> new NoSuchElementException("Coupon Aggregate could not be found"));
    }


    /**
     *
     * @param userId
     * @param couponId
     * @param cartList
     * @return
     */
    private CouponValidationContext loadValidationContext(
            LitemallUserId userId,
            LitemallCouponId couponId,
            List<LitemallCartAggregate> cartList){

        try {
            // Load coupon and user coupon in parallel
            CompletableFuture<LitemallCouponAggregate> couponFuture =
                    CompletableFuture.supplyAsync(() -> couponRepository.findById(couponId).orElseThrow(() -> new NoSuchElementException("Coupon Aggregate could not be found")));

            CompletableFuture<LitemallCouponUserAggregate> couponUserFuture =
                    CompletableFuture.supplyAsync(() -> couponUserRepository.findCouponByUser(couponId, userId).orElseThrow(() -> new NoSuchElementException("User Coupon Aggregate could not be found")));;
            // Batch load all required goods data
            Set<Integer> goodsIds = cartList.stream()
                    .map(item -> item.getGoodsId().getId())
                    .collect(Collectors.toSet());

            Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap = goodsFacade.batchGetGoods(goodsIds);

            // Use the factory method
            return org.linlinjava.litemall.order.domain.model.valueobjects.coupon.CouponValidationContext.create(
                    couponFuture.join(),
                    couponUserFuture.join(),
                    goodsMap
            );

        } catch (Exception e) {
            // Handle exceptions gracefully
            log.error("Failed to load goods from the feign client validation context for user {} and coupon {}",
                    userId, couponId, e);
            return org.linlinjava.litemall.order.domain.model.valueobjects.coupon.CouponValidationContext.mapGoodsNotFound();
        }
    }

}
