package org.linlinjava.litemall.loyalty.domain.events.loyalty;

import lombok.Getter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;

@Getter
public class LitemallExperienceEarnedEvent extends LitemallDomainEvent {

    private final Integer userId;
    private final int experienceEarned;
    private final int newBalance;

    public LitemallExperienceEarnedEvent(Integer userId, int experienceEarned, int newBalance) {
        super("EXPERIENCE_EARNED");
        this.userId = userId;
        this.experienceEarned = experienceEarned;
        this.newBalance = newBalance;
    }
}
