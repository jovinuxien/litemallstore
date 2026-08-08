package org.linlinjava.litemall.goods.infrastructure.configuration;

import java.math.BigDecimal;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Wave-19 promo-candidate knobs ({@code litemall.promo-candidates.*}). The nightly scorer
 * proposes coupon/groupon merchandising candidates over the inventory-intelligence data;
 * proposals are ONLY ever acted on by an admin (create through the existing promotion
 * paths, or dismiss) — nothing is auto-created here.
 */
@Component
@Data
@ConfigurationProperties(prefix = "litemall.promo-candidates")
public class LitemallPromoCandidateProperties {

    /** Kill-switch: env LITEMALL_PROMOCANDIDATES_ENABLED=false stops the nightly scoring. */
    private boolean enabled = true;

    /** Size of the nightly costed-goods scoring pool (best margins first). */
    private int poolLimit = 800;

    /** Max proposals upserted per kind per run. */
    private int maxPerKind = 40;

    /**
     * Guard floor mirrored from promotion's {@code litemall.promotion.coupon.margin-floor}
     * (default 1.05). Suggested coupon rates are bounded by
     * {@code (1 - cost/retail x floor) x 100} so a suggestion can never be rejected by the
     * Wave-18 margin guard. Keep in sync if the promotion floor is ever raised.
     */
    private BigDecimal guardFloor = new BigDecimal("1.05");

    /** Default suggested percent-off for a coupon candidate (bounded by the guard max). */
    private int couponDefaultRate = 10;

    /** Suggested percent-off for clearance candidates (goods with a proposed retirement). */
    private int couponClearanceRate = 20;

    /** Suggestions below this rate are not worth a coupon — the candidate is skipped. */
    private int couponMinRate = 3;

    /** Arrival window (days) used to spot hot arrival categories for category-scoped coupons. */
    private int arrivalWindowDays = 7;

    /** Minimum in-window arrivals for an L1 root to earn a category-scoped suggestion. */
    private int categoryArrivalMin = 20;

    /** Suggested group price = max(cost x guardFloor, retail x this). */
    private BigDecimal grouponDiscount = new BigDecimal("0.80");

    /** No groupon suggestion when the floored group price exceeds retail x this (no real saving). */
    private BigDecimal grouponMaxPriceRatio = new BigDecimal("0.92");

    /** Suggested group-buy window length in days. */
    private int grouponWindowDays = 7;
}
