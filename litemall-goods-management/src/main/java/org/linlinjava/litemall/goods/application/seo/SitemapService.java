package org.linlinjava.litemall.goods.application.seo;

import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.application.goods.CatalogGoodsCountService;
import org.linlinjava.litemall.goods.infrastructure.configuration.PublicSiteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cached sitemaps.org XML for {@code GET /srv/goods/sitemap.xml} (Wave 13): homepage, on-sale
 * L1 category landings ({@code /category/<id>}), and every on-sale product at its slugged
 * canonical URL with {@code lastmod} = update_time. Absolute URLs are minted from
 * {@code litemall.public-base-url}.
 *
 * <p>Serving is a snapshot read (HomeBannerService-style volatile + tryLock — no
 * per-request DB sweep). Regeneration: unconditionally after the nightly catalog refresh
 * ({@code CjCatalogRefreshTask} hook, also covering the post-boot one-shot), lazily when the
 * snapshot passes the staleness fallback, and a warm-up build at startup. A failed build keeps
 * serving the previous bytes (old builtAt intact, so the next read retries); with nothing to
 * fall back to, a homepage-only urlset is cached so a down DB isn't hammered per request —
 * the nightly hook/warm-up replaces it.
 */
@Service
public class SitemapService {

    private static final Logger logger = LoggerFactory.getLogger(SitemapService.class);

    private static final int PAGE_SIZE = 200;
    /** Staleness fallback only — the nightly refresh hook is the intended regeneration path. */
    private static final long STALE_TTL_MS = 24 * 60 * 60 * 1000L;
    /** sitemaps.org caps one file at 50k URLs; warn loudly well before (contract: never truncate silently). */
    private static final int URL_WARN_THRESHOLD = 45_000;

    private final LitemallGoodsService goodsService;
    private final LitemallCategoryService categoryService;
    private final CatalogGoodsCountService goodsCountService;
    private final PublicSiteProperties siteProperties;

    private record Snapshot(byte[] xml, long builtAt) {
    }

    private volatile Snapshot snapshot = null;
    private final ReentrantLock refreshLock = new ReentrantLock();

    public SitemapService(LitemallGoodsService goodsService,
                          LitemallCategoryService categoryService,
                          CatalogGoodsCountService goodsCountService,
                          PublicSiteProperties siteProperties) {
        this.goodsService = goodsService;
        this.categoryService = categoryService;
        this.goodsCountService = goodsCountService;
        this.siteProperties = siteProperties;
    }

    /** Cached sitemap bytes; always valid XML, never throws. */
    public byte[] sitemap() {
        long now = System.currentTimeMillis();
        Snapshot current = snapshot;
        if (current != null && now - current.builtAt() < STALE_TTL_MS) {
            return current.xml();
        }
        if (!refreshLock.tryLock()) {
            // Another thread is building; serve what we have (homepage-only before the first build).
            return current != null ? current.xml() : homepageOnly();
        }
        try {
            current = snapshot;
            if (current != null && System.currentTimeMillis() - current.builtAt() < STALE_TTL_MS) {
                return current.xml();
            }
            try {
                byte[] built = build();
                snapshot = new Snapshot(built, System.currentTimeMillis());
                return built;
            } catch (RuntimeException e) {
                if (current != null) {
                    logger.warn("sitemap rebuild failed; serving previous snapshot", e);
                    return current.xml();
                }
                logger.warn("sitemap build failed with no previous snapshot; caching homepage-only fallback", e);
                byte[] fallback = homepageOnly();
                snapshot = new Snapshot(fallback, System.currentTimeMillis());
                return fallback;
            }
        } finally {
            refreshLock.unlock();
        }
    }

    /** Unconditional build + swap; throws to the caller (the nightly hook catches and logs). */
    public void rebuild() {
        byte[] built = build();
        snapshot = new Snapshot(built, System.currentTimeMillis());
    }

    /** Warm the snapshot off the request path; a failed warm-up must never block boot. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        Thread warmer = new Thread(() -> {
            try {
                sitemap();
            } catch (RuntimeException e) {
                logger.warn("sitemap warm-up failed; first request will retry", e);
            }
        }, "sitemap-warmup");
        warmer.setDaemon(true);
        warmer.start();
    }

    private byte[] build() {
        String base = normalizedBase();
        StringBuilder xml = urlsetOpen();
        int urls = 0;
        appendUrl(xml, base + "/", null);
        urls++;

        Map<Integer, Long> counts = goodsCountService.countsByRoot();
        for (LitemallCategory root : categoryService.queryL1()) {
            Long count = root.getId() != null ? counts.get(root.getId()) : null;
            if (count != null && count > 0) {
                appendUrl(xml, base + "/category/" + root.getId(), null);
                urls++;
            }
        }

        int page = 1;
        while (true) {
            // Page by the unique PK — same walk (and add_time tie-break lesson) as SearchReindexService.
            List<LitemallGoods> batch = goodsService.querySelective(
                    null, null, null, page, PAGE_SIZE, "id", "asc");
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (LitemallGoods goods : batch) {
                if (goods.getId() == null || !Boolean.TRUE.equals(goods.getIsOnSale())) {
                    continue;
                }
                String lastmod = goods.getUpdateTime() != null
                        ? goods.getUpdateTime().toLocalDate().toString()
                        : null;
                appendUrl(xml, base + SeoSlugger.productPath(goods.getId(), goods.getName()), lastmod);
                urls++;
            }
            if (batch.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }
        xml.append("</urlset>\n");
        if (urls >= URL_WARN_THRESHOLD) {
            logger.warn("sitemap holds {} URLs — nearing the 50k sitemaps.org limit; "
                    + "split into a sitemap index before it is exceeded", urls);
        }
        byte[] bytes = xml.toString().getBytes(StandardCharsets.UTF_8);
        logger.info("sitemap rebuilt: {} URLs, {} bytes", urls, bytes.length);
        return bytes;
    }

    private byte[] homepageOnly() {
        StringBuilder xml = urlsetOpen();
        appendUrl(xml, normalizedBase() + "/", null);
        xml.append("</urlset>\n");
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static StringBuilder urlsetOpen() {
        return new StringBuilder(64 * 1024)
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
    }

    private static void appendUrl(StringBuilder xml, String loc, String lastmod) {
        xml.append("  <url><loc>").append(escapeXml(loc)).append("</loc>");
        if (lastmod != null) {
            xml.append("<lastmod>").append(lastmod).append("</lastmod>");
        }
        xml.append("</url>\n");
    }

    private String normalizedBase() {
        String base = siteProperties.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            return "";
        }
        base = base.trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** Slugs are [a-z0-9-] so this is belt-and-braces for the base URL / future entries. */
    private static String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
