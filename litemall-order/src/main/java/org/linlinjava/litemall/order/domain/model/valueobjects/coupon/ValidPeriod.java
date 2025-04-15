package org.linlinjava.litemall.order.domain.model.valueobjects.coupon;

import java.time.LocalDateTime;

public class ValidPeriod {

    private final LocalDateTime startTime;
    private final LocalDateTime endTime;

    public ValidPeriod(LocalDateTime startTime, LocalDateTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public boolean isCurrentPeriodValid() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(startTime) && now.isAfter(endTime);
    }

    public class UsageLimit {

    }
}
