package org.linlinjava.litemall.order.infrastructure.acl.express;

import org.linlinjava.litemall.order.infrastructure.services.acl.facades.ExpressQueryPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.fulfillment.ExpressTrackingSnapshot;

import java.util.Optional;

/**
 * Default (provider {@code none}) express adapter: no live tracking source. The tracking
 * read renders the pre-Wave-4 payload plus {@code note: "tracking provider disabled"}.
 * Plain class, constructed by {@code FulfillmentSeamsConfiguration} — deliberately NOT
 * wrapped in {@code CachingExpressQueryPort} (nothing to cache).
 */
public class NoopExpressQueryAdapter implements ExpressQueryPort {

    @Override
    public Optional<ExpressTrackingSnapshot> query(String carrier, String trackNumber) {
        return Optional.empty();
    }

    @Override
    public boolean enabled() {
        return false;
    }
}
