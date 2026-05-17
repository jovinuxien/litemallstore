package org.linlinjava.litemall.loyalty.domain.model.aggregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.loyalty.domain.events.loyalty.LitemallExperienceEarnedEvent;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallExperienceId;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class LitemallExperienceAggregate {

    private LitemallExperienceId experienceId;
    private LitemallUserId userId;
    private Integer balance;
    private Integer change;
    private String title;
    private String linkId;
    private String linkType;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;

    private List<LitemallDomainEvent> domainEvents = new ArrayList<>();

    /**
     * Earn experience — adds to balance and records a domain event.
     */
    public void earn(int experience, String title, String linkId, String linkType) {
        if (experience <= 0) {
            throw new IllegalArgumentException("Experience to earn must be positive.");
        }
        this.balance = (this.balance == null ? 0 : this.balance) + experience;
        this.change = experience;
        this.title = title;
        this.linkId = linkId;
        this.linkType = linkType;
        this.domainEvents.add(new LitemallExperienceEarnedEvent(
                this.userId.getId(), experience, this.balance));
    }
}
