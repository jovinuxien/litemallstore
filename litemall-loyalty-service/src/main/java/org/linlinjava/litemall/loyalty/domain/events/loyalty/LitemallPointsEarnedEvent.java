package org.linlinjava.litemall.loyalty.domain.events.loyalty;

import lombok.Getter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;

@Getter
public class LitemallPointsEarnedEvent extends LitemallDomainEvent {

    private final Integer userId;
    private final int pointsEarned;
    private final int newBalance;
    private final String title;
    private final String linkId;

    public LitemallPointsEarnedEvent(Integer userId, int pointsEarned, int newBalance, String title, String linkId) {
        super("POINTS_EARNED");
        this.userId = userId;
        this.pointsEarned = pointsEarned;
        this.newBalance = newBalance;
        this.title = title;
        this.linkId = linkId;
    }
}
