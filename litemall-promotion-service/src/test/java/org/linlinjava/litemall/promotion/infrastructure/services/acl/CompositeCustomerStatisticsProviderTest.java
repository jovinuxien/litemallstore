package org.linlinjava.litemall.promotion.infrastructure.services.acl;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.promotion.application.ports.CustomerStatisticsProvider;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerStatistics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for {@code stats.source=composite}: order supplies frequency/monetary
 * truth, Matomo augments recency and contributes engagement-only customers. Uses
 * hand-rolled fakes (no Mockito).
 */
class CompositeCustomerStatisticsProviderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 12, 0);

    private CustomerStatistics stat(int userId, LocalDateTime lastOrderAt, int orders, String spend) {
        return new CustomerStatistics(new LitemallUserId(userId), lastOrderAt, orders,
                new LitemallMoney(new BigDecimal(spend)));
    }

    @Test
    void mergesOrderTruthWithMatomoRecencyAndAddsEngagementOnlyUsers() {
        // User 1 in both: order says 5 orders / 500 spend, last order 60d ago;
        // Matomo says more recent activity 10d ago.
        CustomerStatisticsProvider order = since -> List.of(
                stat(1, NOW.minusDays(60), 5, "500"),
                stat(2, NOW.minusDays(5), 2, "120"));     // order-only user
        CustomerStatisticsProvider matomo = since -> List.of(
                stat(1, NOW.minusDays(10), 1, "30"),       // same user, fresher activity
                stat(3, NOW.minusDays(2), 0, "0"));        // engagement-only user

        CompositeCustomerStatisticsProvider composite =
                new CompositeCustomerStatisticsProvider(order, matomo);

        Map<Integer, CustomerStatistics> byId = composite.fetchSince(NOW).stream()
                .collect(Collectors.toMap(s -> s.getUserId().getId(), Function.identity()));

        assertEquals(3, byId.size(), "union of order + matomo users");

        CustomerStatistics u1 = byId.get(1);
        assertEquals(5, u1.getOrderCount(), "order frequency wins");
        assertEquals(new BigDecimal("500"), u1.getTotalSpend().getAmount(), "order monetary wins");
        assertEquals(NOW.minusDays(10), u1.getLastOrderAt(), "Matomo's fresher recency wins");

        assertEquals(2, byId.get(2).getOrderCount(), "order-only user retained");
        assertEquals(0, byId.get(3).getOrderCount(), "engagement-only user added");
    }
}
