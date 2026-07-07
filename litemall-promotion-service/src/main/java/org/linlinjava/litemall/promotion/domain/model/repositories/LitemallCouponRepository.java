package org.linlinjava.litemall.promotion.domain.model.repositories;

import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;

import java.util.List;
import java.util.Optional;

public interface LitemallCouponRepository {

    Optional<LitemallCouponAggregate> findById(LitemallCouponId couponId);

    /** Coupon definitions a customer may currently claim (NORMAL, not deleted). */
    List<LitemallCouponAggregate> findReceivable();

    /** Resolve a redemption-code coupon by its exact code (exchange-by-code). */
    Optional<LitemallCouponAggregate> findByCode(String code);

    /** Paged listing for the admin surface (newest first). */
    List<LitemallCouponAggregate> findAll(int page, int limit);

    void save(LitemallCouponAggregate coupon);

    /** Logical delete (deleted flag); held user coupons are left untouched. */
    void delete(LitemallCouponId couponId);
}
