package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.goods.domain.model.valueobjects.elastic.ProductDocument;
import org.linlinjava.litemall.goods.domain.service.elastic.CjProductIndexingService;
import org.linlinjava.litemall.goods.domain.service.elastic.ProductIndexer;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Keeps CJ Dropshipping documents current in {@code litemall_index} between full reindexes,
 * via the incremental {@code upsert} path (CJ has no {@code GoodsIndexEvent}, so this is its
 * equivalent of the local write-path → Rabbit → consumer loop). Deliberately separate from the
 * local incremental path.
 *
 * <p>Rate-limit respecting: it reads through {@link CjProductIndexingService} →
 * {@code CJProductService}, whose {@code fetchByCategory} paces each CJ {@code /product/list} call by
 * the configured {@code fetch-pace-seconds} (blocking) — so a multi-category plan stays within the CJ
 * quota even though one run issues several upstream requests. Runs on a config-driven cron
 * ({@code spring.cjdropship.refresh-cron}, default 03:00 nightly).
 *
 * <p>Scope: indexes the configured {@code catalog-targets} (category + per-category limit) via the
 * incremental {@code upsert} path. Stale-CJ-doc deletion (a product removed upstream) is a documented
 * follow-up — this path only upserts.
 *
 * <p>In addition to the nightly cron, a one-shot run fires shortly after startup
 * ({@code spring.cjdropship.refresh-startup-delay-ms}, default 10 min; toggle with
 * {@code refresh-on-startup}) so a freshly deployed/restarted service repopulates the CJ catalog
 * without waiting for 03:00 or an authenticated reindex. It runs off a daemon thread so the paced
 * plan never blocks startup.
 */
@Component
public class CjCatalogRefreshTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(CjCatalogRefreshTask.class);

    private final CjProductIndexingService cjIndexingService;
    private final ProductIndexer productIndexer;
    private final CJDropshippingConfig config;

    public CjCatalogRefreshTask(CjProductIndexingService cjIndexingService,
                                ProductIndexer productIndexer,
                                CJDropshippingConfig config) {
        this.cjIndexingService = cjIndexingService;
        this.productIndexer = productIndexer;
        this.config = config;
    }

    /**
     * Fire a one-shot CJ refresh shortly after the app is ready, on a daemon thread so the paced
     * plan does not block startup. Keeps the index fresh right after a deploy/restart, complementing
     * the nightly cron. Disable with {@code spring.cjdropship.refresh-on-startup=false}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void scheduleStartupRefresh() {
        if (!config.isEnabled() || !config.isRefreshOnStartup()) {
            return;
        }
        long delayMs = Math.max(0, config.getRefreshStartupDelayMs());
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            LOGGER.info("CJ catalog startup refresh firing ({} ms after ready)", delayMs);
            refreshCjCatalog();
        }, "cj-startup-refresh");
        t.setDaemon(true);
        t.start();
        LOGGER.info("CJ catalog startup refresh scheduled {} ms after ready", delayMs);
    }

    @Scheduled(cron = "${spring.cjdropship.refresh-cron:0 0 3 * * *}")
    public void refreshCjCatalog() {
        if (!config.isEnabled()) {
            return;
        }
        try {
            List<ProductDocument> docs = cjIndexingService.buildDocuments();
            int upserted = 0;
            for (ProductDocument doc : docs) {
                productIndexer.upsert(doc);
                upserted++;
            }
            LOGGER.info("CJ catalog refresh upserted {} documents", upserted);
        } catch (RuntimeException ex) {
            LOGGER.warn("CJ catalog refresh failed: {}", ex.getMessage());
        }
    }
}
