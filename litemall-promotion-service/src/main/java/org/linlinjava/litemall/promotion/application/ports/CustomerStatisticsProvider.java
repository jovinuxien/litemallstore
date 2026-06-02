package org.linlinjava.litemall.promotion.application.ports;

import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerStatistics;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Port supplying the per-customer behaviour statistics (R/F/M inputs) the
 * targeting engine scores. The implementation is swappable: Phase 2 reads the
 * litemall-order service via Feign (behind an ACL); Phase 3 swaps/augments this
 * with a Matomo Reporting-API adapter — callers depend only on this port.
 */
public interface CustomerStatisticsProvider {

    /**
     * All customers with order activity since {@code since}, with their order
     * count, total spend, and most-recent order time in that window.
     */
    List<CustomerStatistics> fetchSince(LocalDateTime since);
}
