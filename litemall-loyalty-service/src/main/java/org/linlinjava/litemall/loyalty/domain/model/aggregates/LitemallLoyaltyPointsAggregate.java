package org.linlinjava.litemall.loyalty.domain.model.aggregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.loyalty.domain.events.loyalty.LitemallPointsEarnedEvent;
import org.linlinjava.litemall.loyalty.domain.events.loyalty.LitemallPointsSpentEvent;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallPointsId;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class LitemallLoyaltyPointsAggregate {

    private LitemallPointsId pointsId;
    private LitemallUserId userId;
    private Integer balance;
    private Integer change;
    private String title;
    private String linkId;
    private String linkType;
    private String mark;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;

    private List<LitemallDomainEvent> domainEvents = new ArrayList<>();

    /**
     * Earn points — adds to balance and records a domain event.
     */
    public void earn(int points, String title, String linkId, String linkType) {
        if (points <= 0) {
            throw new IllegalArgumentException("Points to earn must be positive.");
        }
        this.balance = (this.balance == null ? 0 : this.balance) + points;
        this.change = points;
        this.title = title;
        this.linkId = linkId;
        this.linkType = linkType;
        this.domainEvents.add(new LitemallPointsEarnedEvent(
                this.userId.getId(), points, this.balance, title, linkId));
    }

    /**
     * Spend points — subtracts from balance and records a domain event.
     */
    public void spend(int points, String title, String linkId) {
        if (points <= 0) {
            throw new IllegalArgumentException("Points to spend must be positive.");
        }
        int current = this.balance == null ? 0 : this.balance;
        if (current < points) {
            throw new IllegalStateException("Insufficient points balance. Available: " + current + ", requested: " + points);
        }
        this.balance = current - points;
        this.change = -points;
        this.title = title;
        this.linkId = linkId;
        this.domainEvents.add(new LitemallPointsSpentEvent(
                this.userId.getId(), points, this.balance, title));
    }
}
