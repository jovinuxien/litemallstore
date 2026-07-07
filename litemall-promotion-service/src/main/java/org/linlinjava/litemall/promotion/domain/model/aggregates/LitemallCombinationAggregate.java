package org.linlinjava.litemall.promotion.domain.model.aggregates;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCombinationStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Combination (group-buy / 拼团) campaign DEFINITION aggregate. Promotion owns
 * the offer/rules (goods, group price, required members, window); litemall-order
 * owns participation/pink. See the ADR. Backed by litemall-db
 * {@code litemall_combination}.
 */
@Getter
@Setter
@Builder
public class LitemallCombinationAggregate {

    private LitemallCombinationId combinationId;
    private Integer goodsId;
    private String title;
    private String picUrl;
    private LitemallMoney combinationPrice;
    private LitemallMoney originalPrice;
    private Integer requiredMembers;
    private Integer limitPerUser;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LitemallCombinationStatus status;

    @Builder.Default
    private List<LitemallDomainEvent> domainEvents = new ArrayList<>();

    public boolean isActive() {
        return LitemallCombinationStatus.ACTIVE.equals(this.status);
    }

    public boolean isExpired(LocalDateTime now) {
        return this.endTime != null && now.isAfter(this.endTime);
    }

    /**
     * Whether this campaign may currently seed group-buy participation: ACTIVE,
     * inside its window, and requiring at least two members.
     */
    public boolean canStartGroupon(LocalDateTime now) {
        boolean started = startTime == null || !now.isBefore(startTime);
        return isActive() && started && !isExpired(now)
                && requiredMembers != null && requiredMembers >= 2;
    }

    public void activate() {
        if (this.requiredMembers == null || this.requiredMembers < 2) {
            throw new IllegalStateException("A combination campaign needs at least two required members.");
        }
        this.status = LitemallCombinationStatus.ACTIVE;
    }

    public void expire() {
        this.status = LitemallCombinationStatus.EXPIRED;
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
