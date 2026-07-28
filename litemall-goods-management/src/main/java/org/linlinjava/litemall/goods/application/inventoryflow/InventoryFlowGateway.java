package org.linlinjava.litemall.goods.application.inventoryflow;

import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;

import java.util.List;

/**
 * Entry point of the Wave-12 inventory flow (Fisher et al. ch5, messaging gateway):
 * callers hand over what just landed and return immediately — the per-product work
 * rides the flow's executor channel, never the caller's thread beyond the cheap split.
 *
 * <p>Callers MUST wrap invocations in try/catch: a flow failure is bookkept
 * ({@code litemall_cj_sync_run}) but must never break the sync/enrichment path itself.
 */
@MessagingGateway
public interface InventoryFlowGateway {

    /** Nightly/startup catalog cycle landed (post-promote/reconcile). */
    @Gateway(requestChannel = "invflow.catalogLanded")
    void onCatalogLanded(CatalogLandedSummary summary);

    /** A detail-enrichment pass re-promoted these pids (cron batch or on-demand single). */
    @Gateway(requestChannel = "invflow.enrichedPids")
    void onEnrichmentBatch(List<String> pids);
}
