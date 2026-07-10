package org.linlinjava.litemall.promotion.domain.model.aggregates;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
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
