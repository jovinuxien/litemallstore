package org.linlinjava.litemall.goods.domain.service.elastic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Wave-27 {@code eu_flag} basis: a memoized snapshot of the CJ pids whose LAST measured warehouse
 * reading found EU stock, matched against a product's {@code cj_pid} at document-build time — the
 * {@link CouponSignalResolver} / {@link GrouponSignalResolver} pattern applied to Phase-1b's
 * inventory capture (V61 {@code eu_stock_num}).
 *
 * <p><b>What the flag means, precisely.</b> {@code eu_flag=1} = "the last inventory probe of this
 * product found stock in an EU warehouse". {@code eu_flag=0} means <em>not known to hold EU
 * stock</em> — it lumps together "probed, found none" and "never probed", and those are NOT the
 * same thing. That conflation is acceptable for a FILTER (both are products we cannot honestly
 * advertise as EU-stocked) and would be dishonest as a claim about the catalogue, which is why the
 * per-category report keeps them separate over an explicit probed denominator (EuSourcingService).
 *
 * <p>Measured on prod 2026-08-18, the split matters: 16 of the 30 newest arrivals carry DE stock,
 * while 0 of 40 relevance-ordered catalogue products do. Coverage tracks the enrichment rotation,
 * so this flag GROWS over time rather than being right on day one.
 *
 * <p>A stock reading is a measurement with a timestamp, not a promise: it can be stale by the time
 * a customer orders. Surfaces built on it must say "in stock in Germany at last check" rather than
 * turning it into a delivery guarantee.
 *
 * <p>~60s TTL keeps a full reindex at one pid query per minute instead of one per document, and the
 * query returns pid strings only — {@code queryAllLive()} would drag every variants_json and
 * detail_html through memory to answer a yes/no question. Refresh failure is fail-soft: the
 * previous snapshot stands and the flag lags, exactly as the coupon and groupon resolvers do.
 */
@Component
public class EuStockSignalResolver {

    private static final Logger log = LoggerFactory.getLogger(EuStockSignalResolver.class);
    private static final long TTL_MS = 60 * 1000L;

    private final LitemallCjProductService cjProductStore;

    private volatile Set<String> snapshot = Set.of();
    private volatile long builtAt = 0L;

    public EuStockSignalResolver(LitemallCjProductService cjProductStore) {
        this.cjProductStore = cjProductStore;
    }

    /**
     * 1 when this product's CJ snapshot last measured non-zero EU warehouse stock, else 0.
     *
     * <p>Local (non-CJ) goods have no pid and are therefore always 0 — they are not sourced from a
     * CJ warehouse at all, so there is nothing to claim.
     */
    public int euFlag(String cjPid) {
        if (cjPid == null || cjPid.isBlank()) {
            return 0;
        }
        return euStockedPids().contains(cjPid) ? 1 : 0;
    }

    private Set<String> euStockedPids() {
        long now = System.currentTimeMillis();
        Set<String> snap = snapshot;
        if (now - builtAt < TTL_MS) {
            return snap;
        }
        try {
            List<String> pids = cjProductStore.euStockedPids();
            Set<String> fresh = new HashSet<>(pids == null ? List.of() : pids);
            snapshot = fresh;
            builtAt = now;
            return fresh;
        } catch (RuntimeException ex) {
            // Fail-soft: indexing must never die on the EU read — a stale (or empty) snapshot
            // means the flag lags until the next successful refresh.
            log.warn("eu_flag snapshot refresh failed (keeping previous): {}", ex.getMessage());
            builtAt = now;
            return snap;
        }
    }
}
