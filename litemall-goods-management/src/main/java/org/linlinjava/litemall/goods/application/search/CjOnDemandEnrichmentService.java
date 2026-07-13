package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.db.service.LitemallGoodsProductService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Demand-Driven CJ Enrichment — part 1 of 3 (see docs and
 * {@code doc/demand-driven-cj-enrichment-2026-07-13.pdf}).
 *
 * <p>The catalog sync promotes every CJ product as a BROWSABLE shallow goods (one vid-less
 * placeholder SKU); orderability arrives only when enrichment fetches the real variants
 * (1 detail + N inventory CJ calls, ~1 QPS paced ⇒ ~15–30 s per product). Enriching the whole
 * catalog nightly-batch-first would take months, so this service spends the CJ quota in
 * <em>demand order</em> instead: viewing a shallow product (customer detail page, or the order
 * service's goods fetch — including the submit-block retry path) enqueues an asynchronous
 * enrich-in-place. By the time a customer has read the page, the real variants/prices/stock are
 * usually live.
 *
 * <p>Guards: a per-key cooldown (default 6 h — aligned with the CJ raw-cache TTL) so repeat
 * views never re-spend quota; a single worker thread (the CJ-wide RateLimiter in
 * {@code CJProductService} paces the actual calls); a bounded queue that DROPS (with a log)
 * under burst rather than backing up request threads. Everything here is fire-and-forget —
 * a failure only logs; read paths are never affected.
 */
@Service
public class CjOnDemandEnrichmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CjOnDemandEnrichmentService.class);
    private static final int ATTEMPTS_MAP_MAX = 20_000;

    private final CjDetailEnrichmentService enrichmentService;
    private final LitemallGoodsService goodsService;
    private final LitemallGoodsProductService goodsProductService;
    private final CJDropshippingConfig config;

    private final ThreadPoolExecutor worker;
    private final ConcurrentHashMap<String, Long> attempts = new ConcurrentHashMap<>();

    public CjOnDemandEnrichmentService(CjDetailEnrichmentService enrichmentService,
                                       LitemallGoodsService goodsService,
                                       LitemallGoodsProductService goodsProductService,
                                       CJDropshippingConfig config) {
        this.enrichmentService = enrichmentService;
        this.goodsService = goodsService;
        this.goodsProductService = goodsProductService;
        this.config = config;
        this.worker = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(Math.max(1, config.getOnDemandQueueCapacity())),
                r -> {
                    Thread t = new Thread(r, "cj-on-demand-enrich");
                    t.setDaemon(true);
                    return t;
                },
                (r, executor) -> LOGGER.warn("CJ on-demand enrichment queue full — request dropped"));
        this.worker.allowCoreThreadTimeOut(true);
    }

    /**
     * Request enrichment for a promoted goods id if it turns out to be a SHALLOW CJ goods
     * (source CJ, no live SKU carrying a {@code cj_vid}). All checks run on the worker thread —
     * callers pay only a cooldown-map lookup. Safe to call for local goods (no-op).
     */
    public void requestForGoods(int goodsId) {
        if (!enabled() || !markAttempt("g:" + goodsId)) {
            return;
        }
        worker.execute(() -> {
            try {
                enrichIfShallow(goodsId);
            } catch (RuntimeException ex) {
                LOGGER.warn("CJ on-demand enrichment failed for goods {}: {}", goodsId, ex.getMessage());
            }
        });
    }

    /**
     * Request enrichment for a raw CJ pid (the {@code cj_<pid>} OCS-only detail path — the
     * product has no promoted goods row yet; enrichment promotes it in the same pass).
     */
    public void requestForPid(String pid) {
        if (!enabled() || !StringUtils.hasText(pid) || !markAttempt("p:" + pid)) {
            return;
        }
        worker.execute(() -> {
            try {
                enrichmentService.enrichByPid(pid);
            } catch (RuntimeException ex) {
                LOGGER.warn("CJ on-demand enrichment failed for pid {}: {}", pid, ex.getMessage());
            }
        });
    }

    private void enrichIfShallow(int goodsId) {
        LitemallGoods goods = goodsService.findById(goodsId);
        if (goods == null || !StringUtils.hasText(goods.getCjPid())) {
            return; // local goods (or gone) — nothing to do
        }
        List<LitemallGoodsProduct> products = goodsProductService.queryByGid(goodsId);
        for (LitemallGoodsProduct p : products) {
            if (StringUtils.hasText(p.getCjVid())) {
                return; // already orderable — enrichment converged for this goods
            }
        }
        LOGGER.info("CJ on-demand enrichment: shallow goods {} (pid {}) — enriching", goodsId, goods.getCjPid());
        enrichmentService.enrichByPid(goods.getCjPid());
    }

    private boolean enabled() {
        return config.isEnabled() && config.isOnDemandEnrichEnabled();
    }

    /** Records an attempt for the key; {@code false} when still inside the cooldown window. */
    private boolean markAttempt(String key) {
        long now = System.currentTimeMillis();
        long cooldownMs = Math.max(1, config.getOnDemandCooldownSeconds()) * 1000L;
        if (attempts.size() > ATTEMPTS_MAP_MAX) {
            attempts.entrySet().removeIf(e -> now - e.getValue() > cooldownMs);
        }
        Long previous = attempts.putIfAbsent(key, now);
        if (previous == null) {
            return true;
        }
        if (now - previous < cooldownMs) {
            return false;
        }
        return attempts.replace(key, previous, now);
    }
}
