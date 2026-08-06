package org.linlinjava.litemall.promotion.application.internal;

import org.linlinjava.litemall.promotion.application.ports.CouponMarginBasisPort;
import org.linlinjava.litemall.promotion.application.ports.CouponMarginBasisPort.MarginBasis;
import org.linlinjava.litemall.promotion.application.ports.CouponMarginBasisPort.MarginBasisUnavailableException;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponGoodsType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Wave-18 profit guard, run on coupon CREATE and UPDATE (user decision
 * 2026-08-06: HARD BLOCK, no admin override). Worst case = a basket at
 * exactly the {@code min} threshold composed of the worst-ratio in-scope
 * goods. With r = maxCostRatio (from goods-management's margin-basis
 * endpoint) and f = the configured floor:
 *
 * <ul>
 *   <li>flat: {@code D ≤ min × (1 − r×f)}</li>
 *   <li>percent: {@code rate ≤ (1 − r×f) × 100} — the cap bounds absolute
 *       exposure but is NOT a substitute for the rate check</li>
 * </ul>
 *
 * {@code maxCostRatio == null} (nothing costed in scope) ⇒ the guard cannot
 * certify profitability ⇒ reject. goods-management unreachable ⇒ fail CLOSED.
 * Rejection messages state the computed maximum so the admin can adjust;
 * {@code uncostedCount > 0} rides along as a non-blocking warning.
 */
@Component
public class CouponMarginGuard {

    private static final Logger logger = LoggerFactory.getLogger(CouponMarginGuard.class);

    /** Machine-readable rejection codes (surfaced as {@code guardError} in data). */
    public static final String CODE_GUARD_UNAVAILABLE = "GUARD_UNAVAILABLE";
    public static final String CODE_SCOPE_EMPTY = "SCOPE_EMPTY";
    public static final String CODE_SCOPE_UNCOSTED = "SCOPE_UNCOSTED";
    public static final String CODE_DISCOUNT_EXCEEDS_MAX = "DISCOUNT_EXCEEDS_MAX";
    public static final String CODE_INVALID_RATE = "INVALID_RATE";

    private final CouponMarginBasisPort marginBasisPort;
    private final BigDecimal floor;

    public CouponMarginGuard(CouponMarginBasisPort marginBasisPort,
                             @Value("${litemall.promotion.coupon.margin-floor:1.05}") BigDecimal floor) {
        this.marginBasisPort = marginBasisPort;
        this.floor = floor;
    }

    /** Guard verdict; {@code allowed=false} carries a code + admin-facing message. */
    public record GuardDecision(boolean allowed, String code, String message, Integer uncostedCount) {

        static GuardDecision ok(int uncostedCount) {
            return new GuardDecision(true, null, null, uncostedCount > 0 ? uncostedCount : null);
        }

        static GuardDecision reject(String code, String message) {
            return new GuardDecision(false, code, message, null);
        }
    }

    /**
     * Evaluate the guard for a coupon definition about to be saved. The
     * aggregate carries the WOULD-BE state (create fields or merged update).
     */
    public GuardDecision check(LitemallCouponAggregate coupon) {
        BigDecimal discount = coupon.getDiscount() != null
                ? coupon.getDiscount().getAmount() : BigDecimal.ZERO;
        BigDecimal min = coupon.getMin() != null
                ? coupon.getMin().getAmount() : BigDecimal.ZERO;
        boolean percent = coupon.isPercent();

        if (percent) {
            if (discount.compareTo(BigDecimal.ONE) < 0
                    || discount.compareTo(new BigDecimal("90")) > 0) {
                return GuardDecision.reject(CODE_INVALID_RATE,
                        "Percent rate must be between 1 and 90 (got " + discount.stripTrailingZeros().toPlainString() + ")");
            }
        }

        List<Integer> scopeIds = coupon.getGoodsValue() != null
                ? Arrays.stream(coupon.getGoodsValue()).filter(Objects::nonNull).toList()
                : List.of();
        LitemallCouponGoodsType goodsType = coupon.getGoodsType() != null
                ? coupon.getGoodsType() : LitemallCouponGoodsType.ALL;
        List<Integer> goodsIds = List.of();
        List<Integer> categoryIds = List.of();
        if (LitemallCouponGoodsType.CATEGORY.equals(goodsType)) {
            categoryIds = scopeIds;
        } else if (LitemallCouponGoodsType.ARRAY.equals(goodsType)) {
            goodsIds = scopeIds;
        }
        if (!LitemallCouponGoodsType.ALL.equals(goodsType) && scopeIds.isEmpty()) {
            return GuardDecision.reject(CODE_SCOPE_EMPTY,
                    "Scoped coupon has no goods/categories configured — it would match nothing");
        }

        MarginBasis basis;
        try {
            basis = marginBasisPort.fetch(goodsIds, categoryIds);
        } catch (MarginBasisUnavailableException e) {
            logger.warn("margin guard basis unavailable — failing CLOSED: {}", e.getMessage());
            return GuardDecision.reject(CODE_GUARD_UNAVAILABLE,
                    "Margin guard is unavailable — the coupon was NOT saved; try again shortly");
        }

        if (basis.onSaleCount() == 0) {
            return GuardDecision.reject(CODE_SCOPE_EMPTY,
                    "The coupon's scope currently matches no on-sale goods");
        }
        if (basis.maxCostRatio() == null) {
            return GuardDecision.reject(CODE_SCOPE_UNCOSTED,
                    "Cost is not yet captured for this scope (" + basis.uncostedCount()
                            + " of " + basis.onSaleCount()
                            + " goods uncosted) — profitability cannot be certified; retry after the nightly cost rotation");
        }

        // headroom = 1 − r×f  (share of the sale price that is safe to give away)
        BigDecimal headroom = BigDecimal.ONE.subtract(basis.maxCostRatio().multiply(floor));
        if (headroom.compareTo(BigDecimal.ZERO) < 0) {
            headroom = BigDecimal.ZERO;
        }

        if (percent) {
            BigDecimal maxRate = headroom.multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.DOWN);
            if (discount.compareTo(maxRate) > 0) {
                return GuardDecision.reject(CODE_DISCOUNT_EXCEEDS_MAX,
                        "Percent rate " + discount.stripTrailingZeros().toPlainString()
                                + "% would sell below cost x " + floor.toPlainString()
                                + " in this scope — maximum allowed rate is " + maxRate.toPlainString() + "%");
            }
        } else {
            BigDecimal maxDiscount = min.multiply(headroom).setScale(2, RoundingMode.DOWN);
            if (discount.compareTo(maxDiscount) > 0) {
                return GuardDecision.reject(CODE_DISCOUNT_EXCEEDS_MAX,
                        "Discount " + discount.stripTrailingZeros().toPlainString()
                                + " off a " + min.stripTrailingZeros().toPlainString()
                                + " min spend would sell below cost x " + floor.toPlainString()
                                + " in this scope — maximum allowed discount is " + maxDiscount.toPlainString()
                                + (maxDiscount.compareTo(BigDecimal.ZERO) == 0
                                        ? " (raise the min spend to allow a discount)" : ""));
            }
        }

        return GuardDecision.ok(basis.uncostedCount());
    }
}
