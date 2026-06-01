package org.linlinjava.litemall.promotion.domain.model.aggregates;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserCouponId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallUserCouponStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A coupon <em>held by a user</em> (crmeb {@code UserCoupon}): the
 * receive→hold→redeem lifecycle of one issuance. Backed by litemall-db
 * {@code litemall_coupon_user}.
 */
@Getter
@Setter
@Builder
public class LitemallUserCouponAggregate {

    private LitemallUserCouponId userCouponId;
    private LitemallUserId userId;
    private LitemallCouponId couponId;
    private LitemallUserCouponStatus status;
    private LocalDateTime usedTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer orderId;

    @Builder.Default
    private List<LitemallDomainEvent> domainEvents = new ArrayList<>();

    public boolean isUsable() {
        return LitemallUserCouponStatus.USABLE.equals(this.status);
    }

    public boolean isExpired(LocalDateTime now) {
        return this.endTime != null && now.isAfter(this.endTime);
    }

    public boolean isOwnedBy(LitemallUserId candidate) {
        return this.userId != null && this.userId.equals(candidate);
    }

    /**
     * Redeem this held coupon against an order. Transitions USABLE → USED and
     * stamps the order + used time. Rejects a non-usable coupon so a coupon can
     * never be applied twice.
     */
    public void redeem(Integer orderId, LocalDateTime when) {
        if (!isUsable()) {
            throw new IllegalStateException("Coupon is not in a usable state.");
        }
        this.status = LitemallUserCouponStatus.USED;
        this.orderId = orderId;
        this.usedTime = when;
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
