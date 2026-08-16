package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.goods.application.inventoryflow.CatalogLandedSummary;
import org.linlinjava.litemall.goods.application.inventoryflow.CjSyncRunRecorder;
import org.linlinjava.litemall.goods.application.inventoryflow.InventoryFlowGateway;
import org.linlinjava.litemall.goods.application.seo.MetaCatalogFeedService;
import org.linlinjava.litemall.goods.application.seo.SitemapService;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import java.util.List;
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
    private final CategoryImageBackfillService categoryImageBackfill;
    private final CJDropshippingConfig config;
    private final CjSyncRunRecorder runRecorder;
    private final ObjectProvider<InventoryFlowGateway> flowGateway;
    private final SitemapService sitemapService;
    private final MetaCatalogFeedService metaCatalogFeedService;

    public CjCatalogRefreshTask(CjSnapshotSyncService snapshotSyncService,
                                CjDetailEnrichmentService detailEnrichmentService,
                                CjProductPromotionService promotionService,
                                SearchReindexService reindexService,
                                CategoryImageBackfillService categoryImageBackfill,
                                CJDropshippingConfig config,
                                CjSyncRunRecorder runRecorder,
                                ObjectProvider<InventoryFlowGateway> flowGateway,
                                SitemapService sitemapService,
                                MetaCatalogFeedService metaCatalogFeedService) {
        this.snapshotSyncService = snapshotSyncService;
        this.detailEnrichmentService = detailEnrichmentService;
        this.promotionService = promotionService;
        this.reindexService = reindexService;
        this.categoryImageBackfill = categoryImageBackfill;
        this.config = config;
        this.runRecorder = runRecorder;
        this.flowGateway = flowGateway;
        this.sitemapService = sitemapService;
        this.metaCatalogFeedService = metaCatalogFeedService;
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
        Integer runId = runRecorder.open("enrich");
        try {
            CjDetailEnrichmentService.EnrichResult result =
                    detailEnrichmentService.enrichBatch(config.getEnrichBatchSize());
            runRecorder.close(runId, result.enriched() + result.failed(), 0,
                    result.enriched(), 0, result.failed() == 0, null);
        } catch (RuntimeException ex) {
            LOGGER.warn("CJ detail enrichment run failed: {}", ex.getMessage());
            runRecorder.close(runId, 0, 0, 0, 0, false, ex.getMessage());
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


    /**
     * The subset to sweep tonight, or {@code null} meaning "run the full configured plan".
     *
     * <p>Returns null — a FULL run — whenever narrowing the sweep would change behaviour without
     * being asked for: on the configured full-sync day, when the day is unset/unparseable, and
     * crucially when every target is already {@code nightly} (an unconfigured deployment). That
     * last case matters: passing the complete list as an "override" would silently disable
     * stale-pruning and reconcile forever, because both are gated on the override being empty.
     */
    java.util.List<CJDropshippingConfig.CatalogTarget> nightlyTargets() {
        java.util.List<CJDropshippingConfig.CatalogTarget> all = config.getCatalogTargets();
        if (all == null || all.isEmpty()) {
            return null;
        }
        String day = config.getFullSyncDay();
        if (day != null && !day.isBlank()) {
            try {
                if (java.time.DayOfWeek.valueOf(day.trim().toUpperCase(java.util.Locale.ROOT))
                        == java.time.LocalDate.now().getDayOfWeek()) {
                    return null; // weekly full sync: prune + reconcile
                }
            } catch (IllegalArgumentException badDay) {
                LOGGER.warn("CJ refresh: full-sync-day '{}' is not a day of week — running the FULL plan "
                        + "(safe default: a typo must never quietly stop pruning)", day);
                return null;
            }
        } else {
            return null; // unset ⇒ every day is a full run, the pre-Wave-26 behaviour
        }
        java.util.List<CJDropshippingConfig.CatalogTarget> subset = new java.util.ArrayList<>();
        for (CJDropshippingConfig.CatalogTarget t : all) {
            if (t.isNightly()) {
                subset.add(t);
            }
        }
        if (subset.size() == all.size()) {
            return null; // nothing excluded — keep the full-run semantics (prune + reconcile)
        }
        if (subset.isEmpty()) {
            LOGGER.warn("CJ refresh: every target is nightly=false — running the FULL plan rather than "
                    + "fetching nothing");
            return null;
        }
        return subset;
    }

    @Scheduled(cron = "${spring.cjdropship.refresh-cron:0 0 3 * * *}")
    public void refreshCjCatalog() {
        if (!config.isEnabled()) {
            return;
        }
        Integer syncRunId = runRecorder.open("sync");
        try {
            // 1) Fetch (paced, via Redis) → normalize → persist the snapshot; learn live vs vanished pids.
            //
            // Wave 26: on ordinary nights only the `nightly` targets are swept. CJ's daily API points
            // are a hard shared budget (~69k, scaled by ORDER volume rather than catalogue size), and
            // sweeping all ~540 leaves costs ~27k of it to mirror categories the storefront no longer
            // sells — starving enrichment AND order placement, which draw on the same budget.
            List<CJDropshippingConfig.CatalogTarget> nightlyPlan = nightlyTargets();
            boolean fullRun = nightlyPlan == null;
            LOGGER.info("CJ catalog refresh: {} run ({} targets{})",
                    fullRun ? "FULL" : "nightly-subset",
                    fullRun ? config.getCatalogTargets().size() : nightlyPlan.size(),
                    fullRun ? ", stale-prune + reconcile enabled" : ", additive only");
            CjSnapshotSyncService.SyncResult result =
                    fullRun ? snapshotSyncService.syncAll() : snapshotSyncService.syncAll(nightlyPlan);
            runRecorder.close(syncRunId, result.upserted(), result.inserted(), result.updated(),
                    result.removedPids().size(), result.complete(), null);
            syncRunId = null; // closed — a later promote/reindex failure must not rewrite this phase

            // 2) Promote enriched rows into native litemall_goods, and soft-delete native goods for
            //    pids that vanished upstream (only source='cj' rows are ever touched). Reconcile is
            //    trusted ONLY when the sync's fetch plan completed — a partial fetch's livePids set
            //    is a slice of the catalog, and reconciling against a slice mass-deletes the rest
            //    (the 2026-07-13 erosion incident). reconcile() itself also carries a fraction
            //    tripwire as defence in depth.
            CjProductPromotionService.PromoteResult promote = promotionService.promoteBatch(Integer.MAX_VALUE);
            int reconciled = 0;
            if (!fullRun) {
                // A subset run's livePids is a SLICE of the catalogue. Reconciling against it would
                // treat every non-anchor good as vanished — the 2026-07-13 erosion incident, with a
                // different cause. The fraction tripwire inside reconcile() would probably catch it,
                // but "probably caught by a tripwire" is not a design. Prune + reconcile are the
                // weekly full run's job.
                LOGGER.info("CJ refresh: nightly subset — native-goods reconcile skipped (slice, not catalogue)");
            } else if (result.complete()) {
                reconciled = promotionService.reconcile(result.livePids());
                // Wave 26: the reliable half. Absence from a sampled sweep proves nothing, but CJ
                // denying a specific pid twice does — act on that regardless of what reconcile did.
                try {
                    int delisted = promotionService.delistConfirmed();
                    if (delisted > 0) {
                        LOGGER.info("CJ delisting: {} goods removed on CJ's own not-found answers", delisted);
                    }
                } catch (RuntimeException delistEx) {
                    LOGGER.warn("CJ delisting pass failed (refresh continues): {}", delistEx.getMessage());
                }
            } else {
                LOGGER.warn("CJ refresh: fetch plan incomplete — native-goods reconcile skipped this run");
            }

            // Wave 12: hand the landed cycle to the inventory-intelligence flow (metrics, deal
            // proposals, category rollup) — async on its own executor; a flow failure is bookkept
            // in litemall_cj_sync_run and must never break the refresh.
            try {
                InventoryFlowGateway gateway = flowGateway.getIfAvailable();
                if (gateway != null) {
                    gateway.onCatalogLanded(new CatalogLandedSummary(
                            result.insertedPids(), result.removedPids(), result.livePids(),
                            result.upserted(), result.inserted(), result.updated(), result.complete()));
                }
            } catch (RuntimeException flowEx) {
                LOGGER.warn("inventory flow hand-off failed (refresh continues): {}", flowEx.getMessage());
            }

            // 3) Atomically swap the OCS index from the DB: promoted/refreshed goods appear and the
            //    just-soft-deleted ones drop out in one full replace.
            int indexed = reindexService.reindexAll();

            // 4) Newly-landed goods may have filled previously-empty subtrees — give their
            //    categories a representative image (blank-only, idempotent).
            categoryImageBackfill.backfillAll();

            // Wave 13: the sitemap mirrors the on-sale set that just changed — regenerate it
            // now; a sitemap failure must never break the refresh.
            try {
                sitemapService.rebuild();
            } catch (RuntimeException seoEx) {
                LOGGER.warn("sitemap rebuild after refresh failed (refresh continues): {}", seoEx.getMessage());
            }

            // Wave 14.1: the Meta catalogue feed mirrors the same on-sale set — same deal.
            try {
                metaCatalogFeedService.rebuild();
            } catch (RuntimeException feedEx) {
                LOGGER.warn("meta-catalog feed rebuild after refresh failed (refresh continues): {}", feedEx.getMessage());
            }

            LOGGER.info("CJ catalog refresh: {} new / {} updated / {} removed (snapshot); "
                            + "promoted {} (failed {}), reconciled {} stale native goods; reindexed {} docs",
                    result.inserted(), result.updated(), result.removedPids().size(),
                    promote.promoted(), promote.failed(), reconciled, indexed);
        } catch (RuntimeException ex) {
            LOGGER.warn("CJ catalog refresh failed: {}", ex.getMessage());
            runRecorder.close(syncRunId, 0, 0, 0, 0, false, ex.getMessage());
        }
    }
}
