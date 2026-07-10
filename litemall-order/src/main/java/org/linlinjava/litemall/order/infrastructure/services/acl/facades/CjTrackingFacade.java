package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjTrackingSnapshot;

import java.util.Optional;

/**
 * Anti-corruption seam over CJ shipment tracking. Consumers (the tracking endpoints) depend on
 * THIS interface only — never the Feign client — mirroring {@link CjDropshipOrderFacade}.
 * Best-effort by contract: {@link Optional#empty()} means "no tracking info available" (CJ
 * unreachable, unknown tracking number, or an unusable answer) and callers degrade to a clean
 * events-free payload.
 */
public interface CjTrackingFacade {

    /**
     * Tracking summary for one tracking number. Never throws; answers are cached (~1h —
     * tracking moves slowly and CJ enforces ~1 QPS account-wide).
     */
    Optional<CjTrackingSnapshot> trackInfo(String trackNumber);
}
