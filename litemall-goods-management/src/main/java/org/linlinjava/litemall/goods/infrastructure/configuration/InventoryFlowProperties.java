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
}
