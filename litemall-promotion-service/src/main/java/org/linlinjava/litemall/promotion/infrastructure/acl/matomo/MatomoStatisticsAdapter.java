package org.linlinjava.litemall.promotion.infrastructure.acl.matomo;

import org.linlinjava.litemall.promotion.application.ports.CustomerStatisticsProvider;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerStatistics;
import org.linlinjava.litemall.promotion.infrastructure.acl.matomo.dto.MatomoUserStatRow;
import org.linlinjava.litemall.promotion.infrastructure.configuration.MatomoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Anti-corruption layer over {@link MatomoReportingClient}: translates Matomo
 * Reporting-API rows into domain {@link CustomerStatistics} value objects so the
 * targeting engine never sees a Matomo DTO. A {@link CustomerStatisticsProvider}
 * implementation selectable via {@code litemall.promotion.stats.source} (see
 * {@code StatsSourceConfiguration}).
 *
 * <p>Mapping (documented in {@code docs/phase3-marketing-stack-integration.md}):
 * {@code label}→user id, {@code lastActionTimestamp}→recency, {@code nb_conversions}
 * →frequency (order count), {@code revenue}→monetary spend.
 *
 * <p>Degrades gracefully — disabled, a transport failure, or a malformed payload
 * yields an empty population (logged), so an evaluation produces an empty
 * audience rather than throwing through the admin endpoint.
 */
@Component
public class MatomoStatisticsAdapter implements CustomerStatisticsProvider {

    private static final Logger logger = LoggerFactory.getLogger(MatomoStatisticsAdapter.class);

    private final MatomoReportingClient matomoReportingClient;
    private final MatomoProperties properties;

    public MatomoStatisticsAdapter(MatomoReportingClient matomoReportingClient,
                                   MatomoProperties properties) {
        this.matomoReportingClient = matomoReportingClient;
        this.properties = properties;
    }

    @Override
    public List<CustomerStatistics> fetchSince(LocalDateTime since) {
        if (!properties.isEnabled()) {
            logger.debug("Matomo stats source disabled; returning empty population");
            return new ArrayList<>();
        }
        // Matomo selects its window via configured period/date, not the `since`
        // arg (its reporting granularity differs from the order read model); the
        // caller's lookback is honoured by the order adapter. Documented in the
        // Phase-3 runbook.
        try {
            List<MatomoUserStatRow> rows = matomoReportingClient.getUserReport(
                    properties.getReportMethod(),
                    properties.getSiteId(),
                    properties.getPeriod(),
                    properties.getDate(),
                    properties.getAuthToken(),
                    properties.getFilterLimit());
            if (rows == null || rows.isEmpty()) {
                logger.warn("Matomo report empty (method={}, idSite={})",
                        properties.getReportMethod(), properties.getSiteId());
                return new ArrayList<>();
            }
            List<CustomerStatistics> result = new ArrayList<>();
            for (MatomoUserStatRow row : rows) {
                CustomerStatistics stats = toDomain(row);
                if (stats != null) {
                    result.add(stats);
                }
            }
            return result;
        } catch (Exception e) {
            logger.error("Failed to fetch Matomo report (method={}, idSite={}): {}",
                    properties.getReportMethod(), properties.getSiteId(), e.getMessage());
            return new ArrayList<>();
        }
    }

    private CustomerStatistics toDomain(MatomoUserStatRow row) {
        if (row == null) {
            return null;
        }
        Integer userId = parseUserId(row.getLabel());
        if (userId == null || userId <= 0) {
            return null;
        }
        BigDecimal spend = row.getRevenue() != null ? row.getRevenue() : BigDecimal.ZERO;
        if (spend.compareTo(BigDecimal.ZERO) < 0) {
            spend = BigDecimal.ZERO;
        }
        int conversions = row.getNbConversions() != null ? Math.max(0, row.getNbConversions()) : 0;
        LocalDateTime lastActivity = toLocalDateTime(row.getLastActionTimestamp());
        return new CustomerStatistics(
                new LitemallUserId(userId),
                lastActivity,
                conversions,
                new LitemallMoney(spend));
    }

    private Integer parseUserId(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(label.trim());
        } catch (NumberFormatException e) {
            // A non-numeric label (anonymous visitor / username) is not a litemall
            // user id — skip it rather than fail the whole report.
            return null;
        }
    }

    private LocalDateTime toLocalDateTime(Long epochSeconds) {
        if (epochSeconds == null || epochSeconds <= 0) {
            return null;
        }
        return Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
