package org.linlinjava.litemall.order.domain.model.repositories;


import org.linlinjava.litemall.order.domain.model.agregates.LitemallCouponAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallUserId;

import java.util.List;
import java.util.Optional;

public interface LitemallCouponRepository {


    // CRUD operations
    void save(LitemallCouponAggregate couponAggregate);
    int update(LitemallCouponAggregate couponAggregate);
    void remove(LitemallCouponId id);
    Optional<LitemallCouponAggregate> findById(LitemallCouponId id);
    List<LitemallCouponAggregate> queryCouponSelective(String name, Short type, Short status, Integer page, Integer limit, String sort, String order);


    LitemallCouponAggregate findByIdAndUserId(LitemallUserId userId);

    LitemallCouponAggregate findByCode(String code);
    List<LitemallCouponAggregate> findExpiredCoupons();
    int countCoupon(LitemallCouponId couponId);
    List<LitemallCouponAggregate> findAvailableCouponForUser(LitemallUserId userId, int offset, int limit);
}
