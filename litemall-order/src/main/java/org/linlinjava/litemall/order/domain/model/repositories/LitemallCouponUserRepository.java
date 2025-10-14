package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallCouponUserAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCouponUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

import java.util.List;
import java.util.Optional;

public interface LitemallCouponUserRepository {

    int countCoupon(LitemallCouponUserId couponUserId);
    void add(LitemallCouponUserAggregate couponUserAggregate);
    int countUserAndCoupon(LitemallCouponId couponId, LitemallUserId userId);
    int updateCouponUser(LitemallCouponUserAggregate couponUserAggregate);
    LitemallCouponUserAggregate findByOrderId(LitemallOrderId orderId);


    Optional<LitemallCouponUserAggregate> findById(LitemallCouponUserId couponUserId);
    Optional<LitemallCouponUserAggregate> findCouponByUser(LitemallCouponId couponId, LitemallUserId userId);

    List<LitemallCouponUserAggregate> findAll (LitemallCouponId couponId, LitemallUserId userId);
    List<LitemallCouponUserAggregate> findAllByUser(LitemallUserId userId);

    List<LitemallCouponUserAggregate> queryExpiredCoupon();
    List<LitemallCouponUserAggregate> queryListCouponUser(LitemallUserId userId, LitemallCouponId couponId, Short status, Integer page, Integer size, String sort, String order);
}
