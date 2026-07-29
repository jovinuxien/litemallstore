package org.linlinjava.litemall.goods.infrastructure.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Knobs of the Wave-12 CJ inventory-intelligence flow ({@code litemall.inventoryflow.*}).
 * The nightly recheck cron itself is read directly by {@code DealInventoryRecheckTask}'s
 * {@code @Scheduled} expression ({@code litemall.inventoryflow.recheck-cron}, default 04:15 —
 * clear of the 02:45 related-goods, 03:00 sync and 03:30 enrich crons).
 */
@Component
@Data
@ConfigurationProperties(prefix = "litemall.inventoryflow")
public class InventoryFlowProperties {

    /** Bounded capacity of the nightly getInventory recheck queue; overflow is logged, not blocked. */
    private int recheckQueueCapacity = 500;

    /** Per-goods stock bound inside potentialProfit = Σ (retail − cost) × min(stock, cap). */
    private int profitStockCap = 50;

    /** Category-rollup snapshot TTL for request-path reads. */
    private int cacheTtlSeconds = 300;

    /** Debounce for non-forced rollup refreshes (per-product enrichment trickle). */
    private int cacheMinRefreshSeconds = 60;

    // --- Wave 14: inventory governance ---

    /**
     * On-sale CJ catalog size the governor steers toward. When the catalog overshoots,
     * the nightly governor proposes roughly the overage as retire candidates (never fewer);
     * at/below target only hard problems (unavailable streaks) are proposed.
     */
    private int catalogTarget = 12000;

    /** Days of metric history the retirement scorer reads per goods. */
    private int retireWindowDays = 30;

    /** Unavailable streak (days since last available metric row) that hard-proposes retirement. */
    private int retireUnavailableStreakDays = 3;

    /** Don't re-propose a goods this many days after an admin dismissed its retirement. */
    private int retireDismissCooldownDays = 14;

    /**
     * Weekday (java.time.DayOfWeek name, e.g. WEDNESDAY / WED accepted) used as the default
     * execute_on when approving retire candidates without an explicit date — "the next
     * scheduled day". The daily executor cron itself is litemall.inventoryflow.retire-cron.
     */
    private String retireDefaultDay = "WEDNESDAY";
}
