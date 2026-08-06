package org.linlinjava.litemall.promotion.domain.model.aggregates;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponDiscountType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponGoodsType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponStatus;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponTimeType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Coupon <em>definition</em> aggregate (crmeb {@code StoreCoupon}): the offer
 * template an admin issues. The per-user holding/redemption is modelled by
 * {@link LitemallUserCouponAggregate}. Backed by litemall-db
 * {@code litemall_coupon}.
 */
@Getter
@Setter
@Builder
public class LitemallCouponAggregate {

    private LitemallCouponId couponId;
    private String name;
    private String description;
    private String tag;
    /** Total issuable quantity; 0 means unlimited. */
    private Integer total;
    private LitemallMoney discount;
    /**
     * Wave 18 (V51): how {@link #discount} is interpreted — FLAT (amount off,
     * the pre-V51 default) or PERCENT (discount holds the rate 1-90).
     */
    @Builder.Default
    private LitemallCouponDiscountType discountType = LitemallCouponDiscountType.FLAT;
    /** Wave 18 (V51): max absolute discount for PERCENT coupons; null = uncapped. */
    private LitemallMoney discountCap;
    /** Spend threshold (order subtotal must reach this to redeem). */
    private LitemallMoney min;
    /** Max held per user; 0 means unlimited. */
    private Integer limitPerUser;
    private LitemallCouponType type;
    private LitemallCouponStatus status;
    private LitemallCouponGoodsType goodsType;
    private Integer[] goodsValue;
    private String code;
    private LitemallCouponTimeType timeType;
    /** Validity length in days when {@link #timeType} is DAYS. */
    private Integer days;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Builder.Default
    private List<LitemallDomainEvent> domainEvents = new ArrayList<>();

    public boolean isAvailable() {
        return LitemallCouponStatus.NORMAL.equals(this.status);
    }

    public boolean isCodeType() {
        return LitemallCouponType.CODE.equals(this.type);
    }

    public boolean isUnlimitedTotal() {
        return this.total == null || this.total <= 0;
    }

    public boolean isUnlimitedPerUser() {
        return this.limitPerUser == null || this.limitPerUser <= 0;
    }

    /**
     * For an absolute-window coupon, whether {@code now} falls inside the
     * receive window. Relative-days coupons are always receivable while NORMAL.
     */
    public boolean withinReceiveWindow(LocalDateTime now) {
        if (!LitemallCouponTimeType.TIME.equals(this.timeType)) {
            return true;
        }
        boolean afterStart = startTime == null || !now.isBefore(startTime);
        boolean beforeEnd = endTime == null || !now.isAfter(endTime);
        return afterStart && beforeEnd;
    }

    public boolean isPercent() {
        return LitemallCouponDiscountType.PERCENT.equals(this.discountType);
    }

    /**
     * The effective absolute discount this coupon takes off a given order
     * subtotal (Wave 18). FLAT coupons return their configured amount
     * unchanged; PERCENT coupons compute {@code subtotal × rate / 100}
     * (2 dp, HALF_UP) and apply {@link #discountCap} when configured. The
     * result is additionally clamped to the subtotal so a discount can never
     * exceed what is being paid.
     */
    public java.math.BigDecimal computeEffectiveDiscount(LitemallMoney orderSubtotal) {
        java.math.BigDecimal rate = this.discount != null
                ? this.discount.getAmount() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal subtotal = orderSubtotal != null
                ? orderSubtotal.getAmount() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal effective;
        if (isPercent()) {
            effective = subtotal.multiply(rate)
                    .divide(new java.math.BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
            if (this.discountCap != null
                    && effective.compareTo(this.discountCap.getAmount()) > 0) {
                effective = this.discountCap.getAmount();
            }
        } else {
            effective = rate;
        }
        if (effective.compareTo(subtotal) > 0) {
            effective = subtotal;
        }
        return effective.max(java.math.BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Whether an order subtotal meets this coupon's spend threshold. */
    public boolean meetsThreshold(LitemallMoney orderSubtotal) {
        if (this.min == null) {
            return true;
        }
        return this.min.isLessThanOrEqualTo(orderSubtotal);
    }

    /**
     * Whether this coupon's goods scope covers a checkout. {@code goodsValue}
     * holds category ids for CATEGORY scope and goods ids for ARRAY scope; a
     * scoped coupon with no ids configured matches nothing.
     */
    public boolean matchesGoods(List<Integer> goodsIds, List<Integer> categoryIds) {
        if (this.goodsType == null || LitemallCouponGoodsType.ALL.equals(this.goodsType)) {
            return true;
        }
        if (this.goodsValue == null || this.goodsValue.length == 0) {
            return false;
        }
        List<Integer> candidates = LitemallCouponGoodsType.CATEGORY.equals(this.goodsType)
                ? categoryIds : goodsIds;
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }
        for (Integer scoped : this.goodsValue) {
            if (scoped != null && candidates.contains(scoped)) {
                return true;
            }
        }
        return false;
    }

    public void addDomainEvent(LitemallDomainEvent event) {
        this.domainEvents.add(event);
    }

    public List<LitemallDomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        this.domainEvents.clear();
    }
}
