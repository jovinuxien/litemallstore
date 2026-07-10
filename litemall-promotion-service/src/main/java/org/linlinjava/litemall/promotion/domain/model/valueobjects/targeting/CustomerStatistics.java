package org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting;

import lombok.Getter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Per-customer behaviour statistics — the raw R/F/M inputs the targeting engine
 * scores. Sourced behind the {@code CustomerStatisticsProvider} port (Phase 2:
 * order-service via Feign; Phase 3: Matomo). A domain value object so the
 * scoring/segmentation services never depend on an infrastructure DTO.
 */
@Getter
public class CustomerStatistics {

    private final LitemallUserId userId;
    /** Recency input: timestamp of the customer's most recent order (null = never ordered). */
    private final LocalDateTime lastOrderAt;
    /** Frequency input: number of (paid) orders in the observed window. */
    private final int orderCount;
    /** Monetary input: total spend in the observed window. */
    private final LitemallMoney totalSpend;

    public CustomerStatistics(LitemallUserId userId, LocalDateTime lastOrderAt,
                              int orderCount, LitemallMoney totalSpend) {
        this.userId = Objects.requireNonNull(userId, "userId is required");
        this.lastOrderAt = lastOrderAt;
        this.orderCount = Math.max(0, orderCount);
        this.totalSpend = totalSpend != null ? totalSpend : new LitemallMoney(java.math.BigDecimal.ZERO);
    }

    /** Days since the last order relative to {@code now}; large sentinel when never ordered. */
    public long recencyDays(LocalDateTime now) {
        if (lastOrderAt == null) {
            return Long.MAX_VALUE;
        }
        return java.time.Duration.between(lastOrderAt, now).toDays();
    }
}
