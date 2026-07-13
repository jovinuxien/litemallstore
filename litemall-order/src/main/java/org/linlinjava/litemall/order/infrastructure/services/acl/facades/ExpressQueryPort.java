package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.linlinjava.litemall.order.infrastructure.services.acl.facades.fulfillment.ExpressTrackingSnapshot;

import java.util.Optional;

/**
 * Live express-tracking seam for LOCALLY-fulfilled orders (Wave 4, Task D —
 * docs/adr-fulfillment-seams.md). CJ orders keep tracking through
 * {@link CjTrackingFacade}; this port covers the "admin shipped it by hand with a local
 * carrier" case that previously had no live-tracking source. The active adapter (noop vs
 * kdniao vs OnePass, Caffeine-cached) is chosen by {@code litemall.order.express.provider}
 * in {@code FulfillmentSeamsConfiguration}.
 *
 * <p>Contract: {@link #query(String, String)} NEVER throws — {@code Optional.empty()}
 * means "no data / provider miss / provider error", indistinguishable on purpose so the
 * tracking read degrades to the pre-Wave-4 payload (+ an advisory {@code note}).
 */
public interface ExpressQueryPort {

    /** Best-effort live query. Empty = no data / miss / error — adapters never throw. */
    Optional<ExpressTrackingSnapshot> query(String carrier, String trackNumber);

    /** Whether a real provider is configured (false → tracking notes "provider disabled"). */
    boolean enabled();
}
