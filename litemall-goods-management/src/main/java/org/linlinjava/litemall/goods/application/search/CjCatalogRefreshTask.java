package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps CJ Dropshipping documents current in {@code litemall_index} between full reindexes. CJ has no
 * {@code GoodsIndexEvent}, so this scheduled job is its equivalent of the local write-path → Rabbit →
 * consumer loop. Deliberately separate from the local incremental path.
 *
 * <p>Refresh (Phase 4, OCS single-source): (1) {@link CjSnapshotSyncService#syncAll()} pulls the
 * configured {@code catalog-targets} (paced via the Redis staging buffer, respecting the CJ quota) and
 * refreshes the durable {@code litemall_cj_product} snapshot; (2) the enriched snapshot rows are
 * promoted into the native {@code litemall_goods} family ({@link CjProductPromotionService#promoteBatch})
 * and native goods for pids that vanished upstream are soft-deleted
 * ({@link CjProductPromotionService#reconcile}); (3) a full {@link SearchReindexService#reindexAll()}
 * atomically swaps the OCS index from the DB, so promoted/refreshed products appear and soft-deleted
 * ones drop out in one pass — there is no longer any {@code cj_<pid>} document to upsert/delete directly.
 * Runs on a config-driven cron ({@code spring.cjdropship.refresh-cron}, default 03:00 nightly).
 *
 * <p>In addition to the nightly cron, a one-shot run fires shortly after startup
 * ({@code spring.cjdropship.refresh-startup-delay-ms}, default 10 min; toggle with
 * {@code refresh-on-startup}) so a freshly deployed/restarted service repopulates the CJ catalog
 * without waiting for 03:00 or an authenticated reindex. It runs off a daemon thread so the paced
 * plan never blocks startup.
 */
@Component
public class CjCatalogRefreshTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(CjCatalogRefreshTask.class);

    private final CjSnapshotSyncService snapshotSyncService;
    private final CjDetailEnrichmentService detailEnrichmentService;
    private final CjProductPromotionService promotionService;
    private final SearchReindexService reindexService;
    private final CJDropshippingConfig config;

    public CjCatalogRefreshTask(CjSnapshotSyncService snapshotSyncService,
                                CjDetailEnrichmentService detailEnrichmentService,
                                CjProductPromotionService promotionService,
                                SearchReindexService reindexService,
                                CJDropshippingConfig config) {
        this.snapshotSyncService = snapshotSyncService;
        this.detailEnrichmentService = detailEnrichmentService;
        this.promotionService = promotionService;
        this.reindexService = reindexService;
        this.config = config;
    }

    /**
     * Incremental CJ detail+inventory enrichment (config cron {@code spring.cjdropship.enrich-cron},
     * default 03:30 — after the list sync). Enriches a capped batch of the least-recently-enriched rows
     * with real per-SKU prices, real warehouse stock, gallery + attributes, then reindexes them. Capped
     * per run to respect the CJ daily quota; converges over successive runs via the {@code enriched_time}
     * cursor.
     */
    @Scheduled(cron = "${spring.cjdropship.enrich-cron:0 30 3 * * *}")
    public void enrichCjDetails() {
        if (!config.isEnabled()) {
            return;
        }
        try {
            detailEnrichmentService.enrichBatch(config.getEnrichBatchSize());
        } catch (RuntimeException ex) {
            LOGGER.warn("CJ detail enrichment run failed: {}", ex.getMessage());
        }
    }

    /**
     * Fire a one-shot CJ refresh shortly after the app is ready, on a daemon thread so the paced
     * plan does not block startup. Keeps the index fresh right after a deploy/restart, complementing
     * the nightly cron. Disable with {@code spring.cjdropship.refresh-on-startup=false}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void scheduleStartupRefresh() {
        if (!config.isEnabled() || !config.isRefreshOnStartup()) {
            return;
        }
        long delayMs = Math.max(0, config.getRefreshStartupDelayMs());
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            LOGGER.info("CJ catalog startup refresh firing ({} ms after ready)", delayMs);
            refreshCjCatalog();
        }, "cj-startup-refresh");
        t.setDaemon(true);
        t.start();
        LOGGER.info("CJ catalog startup refresh scheduled {} ms after ready", delayMs);
    }

    @Scheduled(cron = "${spring.cjdropship.refresh-cron:0 0 3 * * *}")
    public void refreshCjCatalog() {
        if (!config.isEnabled()) {
            return;
        }
        try {
            // 1) Fetch (paced, via Redis) → normalize → persist the snapshot; learn live vs vanished pids.
            CjSnapshotSyncService.SyncResult result = snapshotSyncService.syncAll();

            // 2) Promote enriched rows into native litemall_goods, and soft-delete native goods for
            //    pids that vanished upstream (only source='cj' rows are ever touched).
            CjProductPromotionService.PromoteResult promote = promotionService.promoteBatch(Integer.MAX_VALUE);
            int reconciled = promotionService.reconcile(result.livePids());

            // 3) Atomically swap the OCS index from the DB: promoted/refreshed goods appear and the
            //    just-soft-deleted ones drop out in one full replace.
            int indexed = reindexService.reindexAll();

            LOGGER.info("CJ catalog refresh: {} new / {} updated / {} removed (snapshot); "
                            + "promoted {} (failed {}), reconciled {} stale native goods; reindexed {} docs",
                    result.inserted(), result.updated(), result.removedPids().size(),
                    promote.promoted(), promote.failed(), reconciled, indexed);
        } catch (RuntimeException ex) {
            LOGGER.warn("CJ catalog refresh failed: {}", ex.getMessage());
        }
    }
}
