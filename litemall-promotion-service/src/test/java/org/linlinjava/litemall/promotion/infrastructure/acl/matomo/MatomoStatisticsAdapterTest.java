package org.linlinjava.litemall.promotion.infrastructure.acl.matomo;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerStatistics;
import org.linlinjava.litemall.promotion.infrastructure.acl.matomo.dto.MatomoUserStatRow;
import org.linlinjava.litemall.promotion.infrastructure.configuration.MatomoProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the Matomo ACL — maps Reporting-API rows to {@link
 * CustomerStatistics} and degrades to an empty population when disabled or the
 * client throws. Uses hand-rolled fakes (no Mockito) and no Spring context.
 */
class MatomoStatisticsAdapterTest {

    private MatomoProperties enabledProps() {
        MatomoProperties p = new MatomoProperties();
        p.setEnabled(true);
        p.setAuthToken("token");
        return p;
    }

    private MatomoUserStatRow row(String label, Integer conversions, String revenue, Long lastTs) {
        MatomoUserStatRow r = new MatomoUserStatRow();
        r.setLabel(label);
        r.setNbConversions(conversions);
        r.setRevenue(revenue != null ? new BigDecimal(revenue) : null);
        r.setLastActionTimestamp(lastTs);
        return r;
    }

    @Test
    void mapsRowsToCustomerStatistics() {
        long ts = LocalDateTime.of(2026, 5, 1, 9, 0)
                .atZone(ZoneId.systemDefault()).toEpochSecond();
        List<MatomoUserStatRow> rows = Arrays.asList(
                row("42", 3, "750.50", ts),
                row("anonymous", 1, "10", ts),   // non-numeric label → skipped
                row("7", null, null, null));      // missing fields → defaults

        MatomoReportingClient client = (method, idSite, period, date, token, limit) -> rows;
        MatomoStatisticsAdapter adapter = new MatomoStatisticsAdapter(client, enabledProps());

        List<CustomerStatistics> stats = adapter.fetchSince(LocalDateTime.now().minusDays(30));

        assertEquals(2, stats.size(), "non-numeric label row should be dropped");

        CustomerStatistics first = stats.get(0);
        assertEquals(42, first.getUserId().getId());
        assertEquals(3, first.getOrderCount());
        assertEquals(new BigDecimal("750.50"), first.getTotalSpend().getAmount());

        CustomerStatistics second = stats.get(1);
        assertEquals(7, second.getUserId().getId());
        assertEquals(0, second.getOrderCount());
        assertEquals(BigDecimal.ZERO, second.getTotalSpend().getAmount());
    }

    @Test
    void returnsEmptyWhenDisabled() {
        MatomoReportingClient client = (method, idSite, period, date, token, limit) -> {
            throw new AssertionError("client must not be called when disabled");
        };
        MatomoStatisticsAdapter adapter = new MatomoStatisticsAdapter(client, new MatomoProperties());
        assertTrue(adapter.fetchSince(LocalDateTime.now()).isEmpty());
    }

    @Test
    void degradesToEmptyOnClientFailure() {
        MatomoReportingClient client = (method, idSite, period, date, token, limit) -> {
            throw new RuntimeException("matomo down");
        };
        MatomoStatisticsAdapter adapter = new MatomoStatisticsAdapter(client, enabledProps());
        assertTrue(adapter.fetchSince(LocalDateTime.now()).isEmpty());
    }
}
