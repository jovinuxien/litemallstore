package org.linlinjava.litemall.goods.application.goods;

import org.linlinjava.litemall.db.domain.LitemallAd;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallAdService;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.infrastructure.configuration.HomeBannerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Homepage banner list (Wave 11): manual admin rows first, then banners DERIVED from the live CJ
 * catalog — never persisted, so the admin ad panel only ever sees real rows.
 *
 * <p>Manual rows ({@code litemall_ad} position=1, enabled) are filtered at read time to URLs
 * starting with {@code https://} or {@code /}: plain-HTTP images (the 3 yanxuan seed rows) are
 * mixed-content-blocked on the HTTPS storefront anyway, so they are dropped here rather than
 * mutated in the DB. Such rows still show "enabled" in the admin panel but are not served.
 *
 * <p>Generated banners: top-N L1 roots by on-sale subtree count (threshold keeps every
 * {@code /category/<id>} link non-empty), hero = newest on-sale goods pic on a CDN-covered CJ host
 * (the gateway rewrites those to {@code /_cdn/...} in the JSON body — raw URLs are stored here).
 * Contract fields: {@code name} = category name, {@code url} = hero pic, {@code link} =
 * SPA-relative {@code /category/<rootId>}, {@code content} = "N products" subtitle,
 * {@code id = -rootId} (synthetic, collision-proof against real ad ids).
 *
 * <p>The generated list is a volatile snapshot with TTL, refreshed under {@code tryLock} — the
 * request path is a snapshot read plus the 3-row ad query, so {@code /srv/goods/index} latency
 * stays flat; concurrent callers during a cold build serve manual-only. Empty and failed builds
 * are cached for a full TTL too (no per-request re-derive storms); failures degrade to the
 * previous snapshot, never a thrown exception.
 */
@Service
public class HomeBannerService {

    private static final Logger logger = LoggerFactory.getLogger(HomeBannerService.class);

    private final LitemallAdService adService;
    private final LitemallCategoryService categoryService;
    private final LitemallGoodsService goodsService;
    private final CatalogGoodsCountService goodsCountService;
    private final HomeBannerProperties properties;

    private record Snapshot(List<LitemallAd> banners, long builtAt) {
    }

    private volatile Snapshot snapshot = null;
    private final ReentrantLock refreshLock = new ReentrantLock();

    public HomeBannerService(LitemallAdService adService,
                             LitemallCategoryService categoryService,
                             LitemallGoodsService goodsService,
                             CatalogGoodsCountService goodsCountService,
                             HomeBannerProperties properties) {
        this.adService = adService;
        this.categoryService = categoryService;
        this.goodsService = goodsService;
        this.goodsCountService = goodsCountService;
        this.properties = properties;
    }

    /** Manual admin banners (filtered), then generated category banners. */
    public List<LitemallAd> homeBanners() {
        List<LitemallAd> banners = new ArrayList<>(filterManual(adService.queryIndex()));
        if (properties.isEnabled()) {
            banners.addAll(generatedSnapshot());
        }
        return banners;
    }

    /** Warm the generated snapshot off the request path; a failed warm-up must never block boot. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        if (!properties.isEnabled()) {
            return;
        }
        Thread warmer = new Thread(() -> {
            try {
                generatedSnapshot();
            } catch (RuntimeException e) {
                logger.warn("home-banner warm-up failed; first request will retry", e);
            }
        }, "home-banner-warmup");
        warmer.setDaemon(true);
        warmer.start();
    }

    /**
     * Servable manual rows: URL must be {@code https://} or site-relative. Plain-HTTP rows are
     * dropped (mixed content); admins entering an http URL will not see it served.
     */
    private List<LitemallAd> filterManual(List<LitemallAd> rows) {
        List<LitemallAd> kept = new ArrayList<>();
        for (LitemallAd row : rows) {
            String url = row.getUrl();
            if (StringUtils.hasText(url) && (url.startsWith("https://") || url.startsWith("/"))) {
                kept.add(row);
            }
        }
        return kept;
    }

    private List<LitemallAd> generatedSnapshot() {
        long now = System.currentTimeMillis();
        Snapshot current = snapshot;
        if (current != null && now - current.builtAt() < properties.getCacheTtlMs()) {
            return current.banners();
        }
        if (!refreshLock.tryLock()) {
            // Another thread is deriving; serve what we have (manual-only before the first build).
            return current != null ? current.banners() : List.of();
        }
        try {
            current = snapshot;
            if (current != null && System.currentTimeMillis() - current.builtAt() < properties.getCacheTtlMs()) {
                return current.banners();
            }
            List<LitemallAd> built;
            try {
                built = deriveBanners();
            } catch (RuntimeException e) {
                logger.warn("home-banner derivation failed; serving previous snapshot", e);
                built = current != null ? current.banners() : List.of();
            }
            snapshot = new Snapshot(built, System.currentTimeMillis());
            return built;
        } finally {
            refreshLock.unlock();
        }
    }

    private List<LitemallAd> deriveBanners() {
        Map<Integer, Long> counts = goodsCountService.countsByRoot();
        List<Map.Entry<Integer, Long>> topRoots = counts.entrySet().stream()
                .filter(e -> e.getValue() >= properties.getMinOnSaleGoods())
                .sorted(Comparator.<Map.Entry<Integer, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(properties.getTopN())
                .toList();
        if (topRoots.isEmpty()) {
            return List.of();
        }

        Map<Integer, String> rootNames = new HashMap<>();
        for (LitemallCategory root : categoryService.queryL1()) {
            if (root.getId() != null && StringUtils.hasText(root.getName())) {
                rootNames.put(root.getId(), root.getName());
            }
        }

        List<LitemallAd> banners = new ArrayList<>();
        for (Map.Entry<Integer, Long> entry : topRoots) {
            Integer rootId = entry.getKey();
            String name = rootNames.get(rootId);
            if (name == null) {
                continue;
            }
            String hero = findHeroImage(rootId);
            if (hero == null) {
                continue;
            }
            LitemallAd banner = new LitemallAd();
            banner.setId(-rootId);
            banner.setName(name);
            banner.setUrl(hero);
            banner.setLink("/category/" + rootId);
            banner.setContent(String.format(Locale.US, "%,d products", entry.getValue()));
            banner.setPosition((byte) 1);
            banner.setEnabled(true);
            banner.setDeleted(false);
            banners.add(banner);
        }
        return banners;
    }

    /**
     * Newest on-sale goods pic in the root's subtree whose host the {@code /_cdn} edge rewrite
     * covers; null if none of the first {@code goodsScanLimit} qualify.
     * NB: queryByCategory's first int is a 1-based PageHelper page number, not an offset.
     */
    private String findHeroImage(Integer rootId) {
        List<LitemallGoods> newest =
                goodsService.queryByCategory(goodsCountService.subtreeIds(rootId), 1, properties.getGoodsScanLimit());
        for (LitemallGoods goods : newest) {
            if (hasAllowedHost(goods.getPicUrl())) {
                return goods.getPicUrl();
            }
        }
        return null;
    }

    private boolean hasAllowedHost(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        try {
            String host = URI.create(url).getHost();
            return host != null && properties.getAllowedImageHosts().stream()
                    .anyMatch(allowed -> allowed.equalsIgnoreCase(host));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
