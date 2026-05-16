package org.linlinjava.litemall.loyalty.domain.events.loyalty;

import lombok.Getter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;

import java.time.LocalDateTime;

@Getter
public class LitemallSignInRewardedEvent extends LitemallDomainEvent {

    private final Integer userId;
    private final int integralEarned;
    private final LocalDateTime signDate;

    public LitemallSignInRewardedEvent(Integer userId, int integralEarned, LocalDateTime signDate) {
        super("SIGN_IN_REWARDED");
        this.userId = userId;
        this.integralEarned = integralEarned;
        this.signDate = signDate;
    }
}
