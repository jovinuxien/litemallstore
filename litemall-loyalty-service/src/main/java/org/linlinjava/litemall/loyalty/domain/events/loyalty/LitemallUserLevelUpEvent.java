package org.linlinjava.litemall.loyalty.domain.events.loyalty;

import lombok.Getter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;

@Getter
public class LitemallUserLevelUpEvent extends LitemallDomainEvent {

    private final Integer userId;
    private final Byte previousGrade;
    private final Byte newGrade;
    private final Integer newLevelId;

    public LitemallUserLevelUpEvent(Integer userId, Byte previousGrade, Byte newGrade, Integer newLevelId) {
        super("USER_LEVEL_UP");
        this.userId = userId;
        this.previousGrade = previousGrade;
        this.newGrade = newGrade;
        this.newLevelId = newLevelId;
    }
}
