package org.linlinjava.litemall.goods.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.search.CjSnapshotSyncService;
import org.linlinjava.litemall.goods.application.search.SearchReindexService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public LitemallSearchAdminController(SearchReindexService reindexService,
                                         CjSnapshotSyncService cjSnapshotSyncService) {
        this.reindexService = reindexService;
        this.cjSnapshotSyncService = cjSnapshotSyncService;
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
                "removed", result.removedPids().size()));
    }
}
