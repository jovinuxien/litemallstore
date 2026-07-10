package org.linlinjava.litemall.promotion.domain.model.repositories;

import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallUserCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallUserCouponStatus;

import java.util.List;
import java.util.Optional;

public interface LitemallUserCouponRepository {

    Optional<LitemallUserCouponAggregate> findById(LitemallUserCouponId id);

    /** How many of a given coupon a user already holds (for per-user limit). */
    int countByUserAndCoupon(LitemallUserId userId, LitemallCouponId couponId);

    /** Total issued for a coupon definition (for the total cap). */
    int countByCoupon(LitemallCouponId couponId);

    List<LitemallUserCouponAggregate> findUsableByUser(LitemallUserId userId);

    /** All coupons a user holds, optionally filtered by status (null = all). */
    List<LitemallUserCouponAggregate> findByUser(LitemallUserId userId, LitemallUserCouponStatus status);

    /** Paged issuance records of a coupon definition (admin, newest first). */
    List<LitemallUserCouponAggregate> findByCoupon(LitemallCouponId couponId, int page, int limit);

    void add(LitemallUserCouponAggregate userCoupon);

    void update(LitemallUserCouponAggregate userCoupon);
}
