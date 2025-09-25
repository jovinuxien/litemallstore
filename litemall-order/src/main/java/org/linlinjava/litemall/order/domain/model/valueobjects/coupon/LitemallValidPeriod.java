package org.linlinjava.litemall.order.domain.model.valueobjects.coupon;

import java.time.LocalDateTime;

public class LitemallValidPeriod {

    private final LocalDateTime startTime;
    private final LocalDateTime endTime;

    public LitemallValidPeriod(LocalDateTime startTime, LocalDateTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public boolean isCurrentPeriodValid() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(startTime) && now.isAfter(endTime);
    }

}
