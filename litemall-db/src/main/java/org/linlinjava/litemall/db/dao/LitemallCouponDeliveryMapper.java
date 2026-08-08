package org.linlinjava.litemall.db.dao;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallCouponDelivery;

/**
 * Hand-written mapper for targeted coupon delivery (Wave 22, V58
 * {@code litemall_coupon_delivery}) plus the wave's two sanctioned read-only
 * shared-table queries (no {@code Example} machinery; same discipline as
 * {@link LitemallPromoCandidateMapper}):
 *
 * <ul>
 *   <li>{@link #selectPaidAudience}: the RFM-style segment over
 *       {@code litemall_order}. "Paid" is the house paid-or-later convention
 *       {@code order_status >= 201} (the SQL analogue of
 *       {@code OrderUtil.hasPayed}; the same set StatMapper and InsightMapper
 *       use) — excludes 101/102/103 unpaid/cancelled and 104 admin-cancel.</li>
 *   <li>{@link #selectPerformance}: conversion measurement over
 *       {@code litemall_coupon_user} + the used rows' orders.</li>
 * </ul>
 */
public interface LitemallCouponDeliveryMapper {

    /** Record one delivery run; generated key lands on {@code delivery.id}. */
    int insertDelivery(LitemallCouponDelivery delivery);

    /**
     * Delivery history, newest first. {@code couponId} null lists every
     * coupon's runs (the admin history view default).
     */
    List<LitemallCouponDelivery> selectByCoupon(@Param("couponId") Integer couponId,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    /** Total rows behind {@link #selectByCoupon} for the page envelope. */
    long countByCoupon(@Param("couponId") Integer couponId);

    /**
     * User ids with paid orders matching every present criterion:
     * {@code paidSince} — last paid activity ({@code coalesce(pay_time, add_time)})
     * at or after the instant; {@code minFrequency} — at least that many paid
     * orders (lifetime); {@code minMonetary} — lifetime paid
     * {@code sum(actual_price)} at least that amount. Null criteria are not
     * applied. Ordered by user id; capped at {@code limit} (callers pass
     * cap+1 to detect overflow).
     */
    List<Integer> selectPaidAudience(@Param("paidSince") LocalDateTime paidSince,
                                     @Param("minFrequency") Integer minFrequency,
                                     @Param("minMonetary") BigDecimal minMonetary,
                                     @Param("limit") int limit);

    /**
     * One row, always present: {@code granted} (all non-deleted coupon_user
     * rows), {@code used} (status 1), {@code ordersCount} + {@code revenue}
     * (count / sum of {@code actual_price} over the used rows' non-deleted
     * orders).
     */
    Map<String, Object> selectPerformance(@Param("couponId") int couponId);
}
