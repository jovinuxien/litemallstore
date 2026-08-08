package org.linlinjava.litemall.promotion.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.db.domain.LitemallCouponDelivery;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.coupon.LitemallGrantCouponCommand;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallCouponDeliveryRepository;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallCouponDeliveryRepository.CouponPerformance;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallCouponDeliveryRepository.DeliveryPage;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallCouponRepository;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallUserCouponRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponTimeType;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Wave-22 targeted coupon delivery + conversion measurement (coupon roadmap
 * Phase 4, CLAUDE.md Wave-22 contract).
 *
 * <p><b>Audience mechanism:</b> the existing targeting seam
 * ({@code CustomerStatisticsProvider} → {@code OrderStatisticsAdapter}) has no
 * working bulk segment query — litemall-order never implemented
 * {@code GET /srv/order/admin/stat/customer-rfm}, so the adapter always
 * degrades to an empty population, which for a delivery run would be a silent
 * deliver-to-nobody. Per the contract's sanctioned fallback, the segment is
 * resolved by a read-only shared-table query over {@code litemall_order} paid
 * statuses (house paid-or-later convention {@code order_status >= 201}); the
 * three criteria are the RAW RFM inputs the targeting engine scores
 * (recency / frequency / monetary — {@code CustomerStatistics}), so a later
 * seam revival can swap the query source without changing this API.
 *
 * <p><b>Grant path:</b> each matched user goes through the EXISTING admin
 * direct-grant ({@link LitemallCouponServiceImpl#grantCoupon} — same
 * validations as {@code POST /grant}). Idempotency rides the per-user claim
 * limit (unlimited treated as ONE for automated sweeps, exactly like
 * register-gifts), so re-delivering the same segment counts already-holding
 * users as skipped instead of double-granting. The margin guard is NOT re-run
 * — the coupon was guarded at create/update.
 *
 * <p><b>Sweep cap:</b> a hard cap ({@code litemall.promotion.coupon.delivery
 * .max-audience}, default 10000) bounds one run; a larger match is a typed
 * refusal (errno {@link #ERRNO_AUDIENCE_TOO_LARGE}) asking the admin to
 * narrow the segment. Deliberately NOT one giant transaction: each grant
 * commits on its own (the grant path's transaction), so a mid-sweep failure
 * never rolls back thousands of already-granted wallets; per-user failures
 * are counted as skipped and the run always completes with an honest ledger
 * row.
 */
@Service
public class CouponDeliveryServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(CouponDeliveryServiceImpl.class);

    /** Bad/missing parameters (at least one criterion is required). */
    public static final int ERRNO_BAD_PARAM = 402;
    /** Coupon id unknown (or logically deleted). */
    public static final int ERRNO_COUPON_NOT_FOUND = 770;
    /** Coupon exists but is expired / withdrawn — refused BEFORE any grant. */
    public static final int ERRNO_COUPON_NOT_DELIVERABLE = 771;
    /** Matched audience exceeds the hard sweep cap. */
    public static final int ERRNO_AUDIENCE_TOO_LARGE = 772;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int SEGMENT_JSON_MAX = 511;

    private final LitemallCouponRepository couponRepository;
    private final LitemallUserCouponRepository userCouponRepository;
    private final LitemallCouponServiceImpl couponService;
    private final LitemallCouponDeliveryRepository deliveryRepository;
    private final int maxAudience;

    public CouponDeliveryServiceImpl(
            LitemallCouponRepository couponRepository,
            LitemallUserCouponRepository userCouponRepository,
            LitemallCouponServiceImpl couponService,
            LitemallCouponDeliveryRepository deliveryRepository,
            @Value("${litemall.promotion.coupon.delivery.max-audience:10000}") int maxAudience) {
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.couponService = couponService;
        this.deliveryRepository = deliveryRepository;
        this.maxAudience = maxAudience;
    }

    // ------------------------------------------------------------------
    // Deliver
    // ------------------------------------------------------------------

    /** Segment criteria; every field optional but at least one required. */
    public record SegmentCriteria(Integer recencyDays, Integer minFrequency,
                                  BigDecimal minMonetary) {

        boolean empty() {
            return recencyDays == null && minFrequency == null && minMonetary == null;
        }
    }

    /** Preview: {@code granted}/{@code skipped} are null (counts only, zero side effects). */
    public record DeliverResult(int matched, Integer granted, Integer skipped) {
    }

    public DeliverResult deliver(int couponId, SegmentCriteria criteria, boolean preview) {
        return deliver(couponId, criteria, preview, LocalDateTime.now());
    }

    DeliverResult deliver(int couponId, SegmentCriteria criteria, boolean preview,
                          LocalDateTime now) {
        validate(criteria);
        LitemallCouponAggregate coupon = requireDeliverable(couponId, now);

        LocalDateTime paidSince = criteria.recencyDays() != null
                ? now.minusDays(criteria.recencyDays()) : null;
        List<Integer> audience = deliveryRepository.selectPaidAudience(
                paidSince, criteria.minFrequency(), criteria.minMonetary(), maxAudience + 1);
        if (audience.size() > maxAudience) {
            throw new CouponDeliveryException(ERRNO_AUDIENCE_TOO_LARGE,
                    "Matched audience exceeds the delivery cap of " + maxAudience
                            + " users — narrow the segment criteria");
        }
        int matched = audience.size();

        if (preview) {
            // Counts only — no grants, no delivery ledger row.
            return new DeliverResult(matched, null, null);
        }

        int granted = 0;
        int skipped = 0;
        int perUserCap = coupon.isUnlimitedPerUser() ? 1 : coupon.getLimitPerUser();
        LitemallCouponId cid = new LitemallCouponId(couponId);
        for (Integer userId : audience) {
            try {
                LitemallUserId uid = new LitemallUserId(userId);
                // Idempotency: at the per-user claim limit ⇒ skipped, never re-granted.
                if (userCouponRepository.countByUserAndCoupon(uid, cid) >= perUserCap) {
                    skipped++;
                    continue;
                }
                // The EXISTING admin direct-grant path — same validations as /grant.
                LitemallPromotionOperationResult result = couponService.grantCoupon(
                        new LitemallGrantCouponCommand(cid, uid));
                if (result.isSuccess()) {
                    granted++;
                } else {
                    // e.g. total supply exhausted mid-sweep — honest skip, keep going.
                    skipped++;
                }
            } catch (RuntimeException e) {
                logger.warn("Coupon {} delivery: grant to user {} failed: {}",
                        couponId, userId, e.getMessage());
                skipped++;
            }
        }

        LitemallCouponDelivery row = new LitemallCouponDelivery();
        row.setCouponId(couponId);
        row.setSegmentJson(segmentJson(criteria));
        row.setMatched(matched);
        row.setGranted(granted);
        row.setSkipped(skipped);
        deliveryRepository.add(row);

        logger.info("Coupon {} delivered to segment {}: matched={}, granted={}, skipped={}",
                couponId, row.getSegmentJson(), matched, granted, skipped);
        return new DeliverResult(matched, granted, skipped);
    }

    private void validate(SegmentCriteria criteria) {
        if (criteria == null || criteria.empty()) {
            throw new CouponDeliveryException(ERRNO_BAD_PARAM,
                    "At least one criterion is required: recencyDays, minFrequency or minMonetary");
        }
        if (criteria.recencyDays() != null && criteria.recencyDays() < 1) {
            throw new CouponDeliveryException(ERRNO_BAD_PARAM, "recencyDays must be >= 1");
        }
        if (criteria.minFrequency() != null && criteria.minFrequency() < 1) {
            throw new CouponDeliveryException(ERRNO_BAD_PARAM, "minFrequency must be >= 1");
        }
        if (criteria.minMonetary() != null
                && criteria.minMonetary().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CouponDeliveryException(ERRNO_BAD_PARAM, "minMonetary must be > 0");
        }
    }

    /**
     * Typed refusals BEFORE any grant: unknown/deleted coupon (770), withdrawn
     * or otherwise non-NORMAL status, or an absolute-window coupon whose end
     * time has passed (771). A not-yet-started absolute window is deliverable
     * — the granted wallets simply become usable at the window start (grant
     * path semantics).
     */
    private LitemallCouponAggregate requireDeliverable(int couponId, LocalDateTime now) {
        Optional<LitemallCouponAggregate> couponOpt =
                couponRepository.findById(new LitemallCouponId(couponId));
        if (couponOpt.isEmpty()) {
            throw new CouponDeliveryException(ERRNO_COUPON_NOT_FOUND, "Coupon not found");
        }
        LitemallCouponAggregate coupon = couponOpt.get();
        if (!coupon.isAvailable()) {
            throw new CouponDeliveryException(ERRNO_COUPON_NOT_DELIVERABLE,
                    "Coupon is not active — cannot deliver");
        }
        if (LitemallCouponTimeType.TIME.equals(coupon.getTimeType())
                && coupon.getEndTime() != null && coupon.getEndTime().isBefore(now)) {
            throw new CouponDeliveryException(ERRNO_COUPON_NOT_DELIVERABLE,
                    "Coupon expired on " + coupon.getEndTime() + " — cannot deliver");
        }
        return coupon;
    }

    private String segmentJson(SegmentCriteria criteria) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (criteria.recencyDays() != null) {
            fields.put("recencyDays", criteria.recencyDays());
        }
        if (criteria.minFrequency() != null) {
            fields.put("minFrequency", criteria.minFrequency());
        }
        if (criteria.minMonetary() != null) {
            fields.put("minMonetary", criteria.minMonetary());
        }
        try {
            String json = JSON.writeValueAsString(fields);
            return json.length() > SEGMENT_JSON_MAX ? json.substring(0, SEGMENT_JSON_MAX) : json;
        } catch (Exception e) {
            return fields.toString();
        }
    }

    // ------------------------------------------------------------------
    // Performance
    // ------------------------------------------------------------------

    /**
     * Null-not-zero rules: {@code redemptionPct} null when nothing granted;
     * {@code avgOrderValue} null when no orders. Money plain decimals (2 dp;
     * pct 1 dp).
     */
    public record PerformanceView(long granted, long used, BigDecimal redemptionPct,
                                  long ordersCount, BigDecimal revenue,
                                  BigDecimal avgOrderValue) {
    }

    public PerformanceView performance(int couponId) {
        if (couponRepository.findById(new LitemallCouponId(couponId)).isEmpty()) {
            throw new CouponDeliveryException(ERRNO_COUPON_NOT_FOUND, "Coupon not found");
        }
        CouponPerformance raw = deliveryRepository.performance(couponId);
        BigDecimal redemptionPct = raw.granted() == 0 ? null
                : BigDecimal.valueOf(raw.used() * 100L)
                        .divide(BigDecimal.valueOf(raw.granted()), 1, RoundingMode.HALF_UP);
        BigDecimal revenue = (raw.revenue() != null ? raw.revenue() : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal avgOrderValue = raw.ordersCount() == 0 ? null
                : revenue.divide(BigDecimal.valueOf(raw.ordersCount()), 2, RoundingMode.HALF_UP);
        return new PerformanceView(raw.granted(), raw.used(), redemptionPct,
                raw.ordersCount(), revenue, avgOrderValue);
    }

    // ------------------------------------------------------------------
    // History
    // ------------------------------------------------------------------

    public DeliveryPage deliveries(Integer couponId, int page, int limit) {
        return deliveryRepository.findDeliveries(couponId, page, limit);
    }

    /** Typed request failure the controller maps onto the errno envelope. */
    public static class CouponDeliveryException extends RuntimeException {
        private final int errno;

        public CouponDeliveryException(int errno, String message) {
            super(message);
            this.errno = errno;
        }

        public int getErrno() {
            return errno;
        }
    }
}
