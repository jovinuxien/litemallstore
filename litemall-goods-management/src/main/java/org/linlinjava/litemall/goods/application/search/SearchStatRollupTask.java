package org.linlinjava.litemall.goods.application.search;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.linlinjava.litemall.db.dao.LitemallSearchStatMapper;
import org.linlinjava.litemall.db.domain.LitemallSearchStatDaily;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSearchStatsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Wave-22 nightly search-stats rollup (default 04:45 — after the 04:30 promo scorer,
 * before the 05:00 auto-deal tick). Recomputes {@code litemall_search_stat_daily} per
 * keyword per day from the two demand sources, then refreshes the demand-derived hot
 * keywords ({@link SearchTrendingService}) under the same kill-switch.
 *
 * <p>Per keyword-day: {@code searches} = logged-in history rows + anonymous consented
 * {@code search} events ({@code user_id IS NULL} only — a logged-in consented search
 * lands in BOTH sources and must be counted once); {@code zeroResults} = history rows
 * with {@code result_count = 0} (NULL = unknown, never zero); {@code clicks} = all
 * {@code click_result} events carrying a query (their single source). Keywords are
 * normalized (trim + lowercase, 127 cap) in the source SQL and defensively re-normalized
 * when merging. The upsert overwrites counts absolutely, so re-rolling a day is
 * idempotent. Aggregate-only: no visitor/user identity leaves the source queries.
 */
@Component
public class SearchStatRollupTask {

    private static final Logger log = LoggerFactory.getLogger(SearchStatRollupTask.class);

    static final int KEYWORD_MAX_LENGTH = 127;

    private final LitemallSearchStatMapper statMapper;
    private final SearchTrendingService trendingService;
    private final LitemallSearchStatsProperties properties;

    public SearchStatRollupTask(LitemallSearchStatMapper statMapper,
                                SearchTrendingService trendingService,
                                LitemallSearchStatsProperties properties) {
        this.statMapper = statMapper;
        this.trendingService = trendingService;
        this.properties = properties;
    }

    @Scheduled(cron = "${litemall.search-stats.cron:0 45 4 * * *}")
    public void tick() {
        try {
            runNightly();
        } catch (RuntimeException ex) {
            log.warn("search-stats tick failed (next run retries): {}", ex.getMessage());
        }
    }

    /** The nightly pass: trailing-days rollup, then the trending refresh (same kill-switch). */
    public Map<String, Object> runNightly() {
        Map<String, Object> summary = runRollup(null);
        if (Boolean.TRUE.equals(summary.get("enabled"))) {
            summary.put("trending", trendingService.refresh());
        }
        return summary;
    }

    /**
     * Rollup only ({@code day} null = the trailing {@code rollupDays} window ending today) —
     * also the manual insight trigger. Honest summary out; kill-switch refuses honestly.
     */
    public Map<String, Object> runRollup(LocalDate day) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (!properties.isEnabled()) {
            log.info("search-stats: disabled by kill-switch — nothing rolled up");
            summary.put("enabled", false);
            return summary;
        }
        summary.put("enabled", true);
        LocalDate today = LocalDate.now();
        LocalDate from = day != null ? day : today.minusDays(Math.max(0, properties.getRollupDays() - 1));
        LocalDate to = day != null ? day : today;
        int keywords = 0;
        long searches = 0;
        long zeroResults = 0;
        long clicks = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Map<String, long[]> agg = aggregateDay(d);
            for (Map.Entry<String, long[]> entry : agg.entrySet()) {
                statMapper.upsertDay(toStat(d, entry.getKey(), entry.getValue()));
                keywords++;
                searches += entry.getValue()[0];
                zeroResults += entry.getValue()[1];
                clicks += entry.getValue()[2];
            }
        }
        summary.put("from", from.toString());
        summary.put("to", to.toString());
        summary.put("keywords", keywords);
        summary.put("searches", searches);
        summary.put("zeroResults", zeroResults);
        summary.put("clicks", clicks);
        log.info("search-stats rollup {}..{}: {} keyword-days upserted ({} searches, {} zero-result, {} clicks)",
                from, to, keywords, searches, zeroResults, clicks);
        return summary;
    }

    /** One day's merged aggregate: keyword -> [searches, zeroResults, clicks]. */
    private Map<String, long[]> aggregateDay(LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();
        Map<String, long[]> agg = new LinkedHashMap<>();
        for (Map<String, Object> row : safe(statMapper.selectHistoryAgg(start, end))) {
            String keyword = normalizeKeyword(row.get("keyword"));
            if (keyword == null) {
                continue;
            }
            long[] counts = agg.computeIfAbsent(keyword, k -> new long[3]);
            counts[0] += asLong(row.get("searches"));
            counts[1] += asLong(row.get("zeroResults"));
        }
        for (Map<String, Object> row : safe(statMapper.selectSearchEventAgg(start, end))) {
            String keyword = normalizeKeyword(row.get("keyword"));
            if (keyword == null) {
                continue;
            }
            agg.computeIfAbsent(keyword, k -> new long[3])[0] += asLong(row.get("searches"));
        }
        for (Map<String, Object> row : safe(statMapper.selectClickEventAgg(start, end))) {
            String keyword = normalizeKeyword(row.get("keyword"));
            if (keyword == null) {
                continue;
            }
            agg.computeIfAbsent(keyword, k -> new long[3])[2] += asLong(row.get("clicks"));
        }
        return agg;
    }

    private LitemallSearchStatDaily toStat(LocalDate day, String keyword, long[] counts) {
        LitemallSearchStatDaily stat = new LitemallSearchStatDaily();
        stat.setDay(day);
        stat.setKeyword(keyword);
        stat.setSearches(clampInt(counts[0]));
        stat.setZeroResults(clampInt(counts[1]));
        stat.setClicks(clampInt(counts[2]));
        return stat;
    }

    /** Normalization contract: trim + lowercase, cap {@value KEYWORD_MAX_LENGTH}; null when blank. */
    static String normalizeKeyword(Object raw) {
        if (raw == null) {
            return null;
        }
        String keyword = raw.toString().trim().toLowerCase(Locale.ROOT);
        if (keyword.isEmpty()) {
            return null;
        }
        return keyword.length() > KEYWORD_MAX_LENGTH ? keyword.substring(0, KEYWORD_MAX_LENGTH) : keyword;
    }

    private static List<Map<String, Object>> safe(List<Map<String, Object>> rows) {
        return rows == null ? List.of() : rows;
    }

    private static long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static int clampInt(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }
}
