package org.linlinjava.litemall.order.infrastructure.acl.express;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.ExpressQueryPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.fulfillment.ExpressTrackingSnapshot;

import java.time.Duration;
import java.util.Optional;

/**
 * Caffeine decorator around a real {@link ExpressQueryPort} adapter (the
 * {@code CjTrackingFacadeImpl} pattern): tracking pages re-render far more often than
 * parcels move, and both kdniao and OnePass meter calls. Keyed {@code carrier|trackNumber};
 * NEGATIVES ARE CACHED TOO (an empty answer is an answer — it stops a provider outage or
 * an unknown number from hammering the vendor on every page render). TTL comes from
 * {@code litemall.order.express.cache-minutes} (default 30).
 */
public class CachingExpressQueryPort implements ExpressQueryPort {

    private final ExpressQueryPort delegate;
    private final Cache<String, Optional<ExpressTrackingSnapshot>> cache;

    public CachingExpressQueryPort(ExpressQueryPort delegate, Duration ttl) {
        this.delegate = delegate;
        this.cache = Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(ttl)
                .build();
    }

    @Override
    public Optional<ExpressTrackingSnapshot> query(String carrier, String trackNumber) {
        if (trackNumber == null || trackNumber.isBlank()) {
            return Optional.empty();
        }
        String key = (carrier == null ? "" : carrier.trim()) + "|" + trackNumber.trim();
        return cache.get(key, k -> delegate.query(carrier, trackNumber));
    }

    @Override
    public boolean enabled() {
        return delegate.enabled();
    }
}
