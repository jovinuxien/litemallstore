package org.linlinjava.litemall.order.domain.model.valueobjects.groupon;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

import java.time.LocalDateTime;

@Getter
@Setter
public class GrouponParticipant {
    private LitemallUserId userId;
    private LocalDateTime joinTime;
    private boolean paid;

    public GrouponParticipant(LitemallUserId userId, LocalDateTime joinTime) {
        this.userId = userId;
        this.joinTime = joinTime;
        this.paid = false;
    }

    public void markAsPaid() {
        this.paid = true;
    }
}
