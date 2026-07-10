package org.linlinjava.litemall.promotion.infrastructure.services.acl;

import org.linlinjava.litemall.promotion.application.ports.CustomerStatisticsProvider;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges two {@link CustomerStatisticsProvider}s by user id (Phase 3
 * {@code stats.source=composite}). The order provider supplies the transactional
 * truth — order count (frequency) and spend (monetary) — while the Matomo
 * provider augments recency (most recent activity across both sources) and
 * contributes engagement-only customers the order read model has not yet seen.
 *
 * <p>Constructed by {@code StatsSourceConfiguration}; not a component itself, so
 * it never competes for injection with its two delegates.
 */
public class CompositeCustomerStatisticsProvider implements CustomerStatisticsProvider {

    private static final Logger logger = LoggerFactory.getLogger(CompositeCustomerStatisticsProvider.class);

    private final CustomerStatisticsProvider orderProvider;
    private final CustomerStatisticsProvider matomoProvider;

    public CompositeCustomerStatisticsProvider(CustomerStatisticsProvider orderProvider,
                                               CustomerStatisticsProvider matomoProvider) {
        this.orderProvider = orderProvider;
        this.matomoProvider = matomoProvider;
    }

    @Override
    public List<CustomerStatistics> fetchSince(LocalDateTime since) {
        Map<Integer, CustomerStatistics> merged = new LinkedHashMap<>();

        for (CustomerStatistics s : orderProvider.fetchSince(since)) {
            merged.put(s.getUserId().getId(), s);
        }
        for (CustomerStatistics m : matomoProvider.fetchSince(since)) {
            merged.merge(m.getUserId().getId(), m, this::combine);
        }

        logger.info("Composite stats: {} customers after merging order + Matomo", merged.size());
        return new ArrayList<>(merged.values());
    }

    /**
     * Combine an order record ({@code order}) with a Matomo record ({@code matomo})
     * for the same user: keep order's frequency/monetary truth, take the more
     * recent activity of the two as recency.
     */
    private CustomerStatistics combine(CustomerStatistics order, CustomerStatistics matomo) {
        LocalDateTime recency = mostRecent(order.getLastOrderAt(), matomo.getLastOrderAt());
        return new CustomerStatistics(
                order.getUserId(),
                recency,
                order.getOrderCount(),
                order.getTotalSpend());
    }

    private LocalDateTime mostRecent(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }
}
