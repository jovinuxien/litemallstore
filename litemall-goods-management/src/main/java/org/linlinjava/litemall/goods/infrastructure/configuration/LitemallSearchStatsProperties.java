package org.linlinjava.litemall.goods.infrastructure.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Wave-22 search demand analytics knobs ({@code litemall.search-stats.*}). The nightly
 * rollup aggregates {@code litemall_search_history} and the Phase-0 behavioral log into
 * {@code litemall_search_stat_daily}, then the trending refresh derives hot keywords from
 * real demand (additive over the curated {@code litemall_keyword} set — admin rows are
 * never deleted or unhotted). Cron default 04:45 — after the 04:30 promo scorer, before
 * the 05:00 auto-deal tick.
 */
@Component
@Data
@ConfigurationProperties(prefix = "litemall.search-stats")
public class LitemallSearchStatsProperties {

    /** Kill-switch: env LITEMALL_SEARCHSTATS_ENABLED=false stops the nightly rollup + trending refresh. */
    private boolean enabled = true;

    /**
     * How many trailing days the nightly run recomputes (today inclusive). 2 = yesterday
     * gets its final counts, today gets a fresh partial (overwritten again tomorrow —
     * the upsert is an absolute recompute, so re-rolling is idempotent). Also absorbs
     * late-arriving behavioral events (occurred_at is clamped up to 7d back at ingest).
     */
    private int rollupDays = 2;

    /** Top-N demand queries the trending refresh promotes into hot keywords. */
    private int trendingTop = 8;

    /** Demand window (days) the trending refresh ranks over. */
    private int trendingWindowDays = 7;

    /** A query needs at least this many in-window searches to become a hot keyword. */
    private int trendingMinSearches = 3;
}
