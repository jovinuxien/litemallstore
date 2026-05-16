package org.linlinjava.litemall.loyalty.domain.model.aggregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallSignId;
import org.linlinjava.litemall.loyalty.domain.model.valueobjects.LitemallUserId;

import java.time.LocalDateTime;

@Getter
@Setter
public class LitemallSignInAggregate {

    private LitemallSignId signId;
    private LitemallUserId userId;
    private Integer integral;
    private LocalDateTime signDate;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;

    /**
     * Factory method to create a new sign-in record.
     */
    public static LitemallSignInAggregate create(LitemallUserId userId, Integer integral, LocalDateTime signDate) {
        if (userId == null) {
            throw new IllegalArgumentException("UserId must not be null.");
        }
        if (integral == null || integral < 0) {
            throw new IllegalArgumentException("Integral must be non-negative.");
        }
        LitemallSignInAggregate aggregate = new LitemallSignInAggregate();
        aggregate.setUserId(userId);
        aggregate.setIntegral(integral);
        aggregate.setSignDate(signDate != null ? signDate : LocalDateTime.now());
        aggregate.setAddTime(LocalDateTime.now());
        aggregate.setUpdateTime(LocalDateTime.now());
        return aggregate;
    }
}
