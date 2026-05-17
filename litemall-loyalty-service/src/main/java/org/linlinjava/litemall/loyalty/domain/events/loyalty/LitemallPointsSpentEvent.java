package org.linlinjava.litemall.loyalty.domain.events.loyalty;

import lombok.Getter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;

@Getter
public class LitemallPointsSpentEvent extends LitemallDomainEvent {

    private final Integer userId;
    private final int pointsSpent;
    private final int newBalance;
    private final String title;

    public LitemallPointsSpentEvent(Integer userId, int pointsSpent, int newBalance, String title) {
        super("POINTS_SPENT");
        this.userId = userId;
        this.pointsSpent = pointsSpent;
        this.newBalance = newBalance;
        this.title = title;
    }
}
