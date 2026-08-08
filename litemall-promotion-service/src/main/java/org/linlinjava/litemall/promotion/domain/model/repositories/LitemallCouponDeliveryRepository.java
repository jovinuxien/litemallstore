package org.linlinjava.litemall.promotion.domain.model.repositories;

import org.linlinjava.litemall.db.domain.LitemallCouponDelivery;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Wave-22 targeted-delivery persistence + the wave's two sanctioned read-only
 * shared-table queries (CLAUDE.md Wave-22 contract: the promotion targeting
 * seam has no working bulk segment query — litemall-order never implemented
 * {@code /srv/order/admin/stat/customer-rfm}, so {@code OrderStatisticsAdapter}
 * always degrades to an empty population — hence the sanctioned direct read
 * over {@code litemall_order} paid statuses).
 *
 * <p>"Paid" is the house paid-or-later convention {@code order_status >= 201}
 * (StatMapper / InsightMapper; SQL analogue of {@code OrderUtil.hasPayed}).
 */
public interface LitemallCouponDeliveryRepository {

    /**
     * User ids whose PAID order history satisfies every non-null criterion:
     * last paid activity at/after {@code paidSince}; at least
     * {@code minFrequency} lifetime paid orders; lifetime paid spend of at
     * least {@code minMonetary}. Capped at {@code limit} (pass cap+1 to
     * detect overflow), ordered by user id.
     */
    List<Integer> selectPaidAudience(LocalDateTime paidSince, Integer minFrequency,
                                     BigDecimal minMonetary, int limit);

    /** Record a completed (non-preview) delivery run; the generated id lands on the row. */
    void add(LitemallCouponDelivery delivery);

    /** Delivery history page, newest first; {@code couponId} null = all coupons. */
    DeliveryPage findDeliveries(Integer couponId, int page, int limit);

    /** Conversion measurement for one coupon (always present, zeros when nothing granted). */
    CouponPerformance performance(int couponId);

    /** Page envelope backing rows for the deliveries history endpoint. */
    record DeliveryPage(long total, List<LitemallCouponDelivery> rows) {
    }

    /**
     * Raw performance counters: {@code granted} = non-deleted
     * {@code litemall_coupon_user} rows, {@code used} = rows with status USED,
     * {@code ordersCount}/{@code revenue} = count / {@code actual_price} sum of
     * the used rows' non-deleted orders. Ratios (redemption %, AOV) are derived
     * by the application service so the null-not-zero rules live in one place.
     */
    record CouponPerformance(long granted, long used, long ordersCount, BigDecimal revenue) {
    }
}
