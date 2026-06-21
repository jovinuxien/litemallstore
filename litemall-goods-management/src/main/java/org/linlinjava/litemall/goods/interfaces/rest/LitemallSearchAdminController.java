package org.linlinjava.litemall.goods.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.search.CjDetailEnrichmentService;
import org.linlinjava.litemall.goods.application.search.CjProductPromotionService;
import org.linlinjava.litemall.goods.application.search.CjSnapshotSyncService;
import org.linlinjava.litemall.goods.application.search.SearchReindexService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin-only search maintenance. Mapped under {@code /srv/private/admin/**} so
 * the ADMIN gate is enforced by both litemall-gateway-admin and the
 * litemall-svcsecurity resource-server filter chain (both require
 * {@code ROLE_ADMIN} on that path prefix). Path-based gating is the app's
 * convention; no method-level {@code @PreAuthorize} is wired.
 */
@RestController
@RequestMapping("/srv/private/admin/search")
public class LitemallSearchAdminController {

    private final SearchReindexService reindexService;
    private final CjSnapshotSyncService cjSnapshotSyncService;
    private final CjDetailEnrichmentService cjDetailEnrichmentService;
    private final CjProductPromotionService cjProductPromotionService;

    public LitemallSearchAdminController(SearchReindexService reindexService,
                                         CjSnapshotSyncService cjSnapshotSyncService,
                                         CjDetailEnrichmentService cjDetailEnrichmentService,
                                         CjProductPromotionService cjProductPromotionService) {
        this.reindexService = reindexService;
        this.cjSnapshotSyncService = cjSnapshotSyncService;
        this.cjDetailEnrichmentService = cjDetailEnrichmentService;
        this.cjProductPromotionService = cjProductPromotionService;
    }

    /**
     * Full reindex into OCS from the DB (local goods + the persisted CJ snapshot). Fast — it does NOT
     * call the CJ API; it indexes whatever CJ rows are currently live in {@code litemall_cj_product}.
     * Populate/refresh that snapshot first via {@code POST /cj-sync} (or the nightly cron).
     */
    @PostMapping("/reindex")
    public Object reindex() {
        int count = reindexService.reindexAll();
        return ResponseUtil.ok(Map.of("indexed", count));
    }

    /**
     * Refresh the CJ Dropshipping snapshot from the CJ API (paced, via the Redis staging buffer) and
     * persist it into {@code litemall_cj_product}. This is the slow, rate-limited, API-touching half
     * of the nightly job, exposed for on-demand admin runs; follow with {@code /reindex} (or wait for
     * the cron) to push the refreshed snapshot into OCS. Returns rows upserted + stale pids removed.
     */
    @PostMapping("/cj-sync")
    public Object cjSync() {
        CjSnapshotSyncService.SyncResult result = cjSnapshotSyncService.syncAll();
        return ResponseUtil.ok(Map.of(
                "upserted", result.upserted(),
                "inserted", result.inserted(),
                "updated", result.updated(),
                "removed", result.removedPids().size()));
    }

    /**
     * Run one incremental CJ detail+inventory enrichment batch on demand (the same work the
     * {@code enrich-cron} job does): for the least-recently-enriched CJ rows, fetch real per-variant
     * prices + warehouse stock + gallery/attributes (paced, via Redis), persist, and reindex. This
     * touches the rate-limited CJ API ({@code 1 detail + N inventory} calls per product), so the
     * {@code batch} is small by default — keep it within the CJ daily quota.
     */
    @PostMapping("/cj-enrich")
    public Object cjEnrich(@RequestParam(name = "batch", defaultValue = "20") int batch) {
        CjDetailEnrichmentService.EnrichResult result = cjDetailEnrichmentService.enrichBatch(batch);
        return ResponseUtil.ok(Map.of(
                "enriched", result.enriched(),
                "failed", result.failed()));
    }

    /**
     * Promote already-enriched CJ snapshot rows into the native {@code litemall_goods} family so OCS
     * and the storefront read the DB only. Pure DB work — no CJ API call — and idempotent (re-running
     * updates in place), so {@code batch} can be large for a backfill. Follow with {@code /reindex} to
     * push the freshly-promoted goods into OCS.
     */
    @PostMapping("/cj-promote")
    public Object cjPromote(@RequestParam(name = "batch", defaultValue = "200") int batch) {
        CjProductPromotionService.PromoteResult result = cjProductPromotionService.promoteBatch(batch);
        return ResponseUtil.ok(Map.of(
                "promoted", result.promoted(),
                "failed", result.failed(),
                "total", result.total()));
    }
}
