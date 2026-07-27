package org.linlinjava.litemall.goods.application.inventoryflow;

import java.util.List;
import java.util.Set;

/**
 * Gateway payload (Wave 12): what a completed catalog sync+promote cycle landed.
 * Mirrors {@code CjSnapshotSyncService.SyncResult} — {@code insertedPids} become
 * NEW_ARRIVAL events, {@code livePids − insertedPids} UPDATED, {@code removedPids} VANISHED.
 */
public record CatalogLandedSummary(List<String> insertedPids,
                                   List<String> removedPids,
                                   Set<String> livePids,
                                   int upserted,
                                   int inserted,
                                   int updated,
                                   boolean complete) {
}
