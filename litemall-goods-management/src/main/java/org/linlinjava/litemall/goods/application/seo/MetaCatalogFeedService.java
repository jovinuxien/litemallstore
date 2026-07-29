package org.linlinjava.litemall.goods.application.seo;

import org.linlinjava.litemall.db.dao.LitemallGoodsProductMapper;
import org.linlinjava.litemall.db.domain.LitemallBrand;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.db.domain.LitemallGoodsProductExample;
import org.linlinjava.litemall.db.service.LitemallBrandService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.linlinjava.litemall.goods.infrastructure.configuration.PublicSiteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cached Meta Commerce Manager product feed for {@code GET /srv/goods/meta-catalog.csv}
 * (Wave 14.1, contract: {@code doc/meta-catalog-feed.md}): one CSV row per on-sale
 * product, 13 exact columns, prices in the store's real charging currency (the same
 * {@code litemall.goods.currency} the meta endpoint serves), slugged PDP links from
 * {@link SeoSlugger}, and image links absolutized onto the {@code /_cdn} proxy paths —
 * the edge's JSON-only rewrite filter never touches CSV, so the feed must mint final
 * URLs itself.
 *
 * <p>Serving mirrors {@link SitemapService}: a volatile snapshot regenerated after the
 * nightly catalog refresh (plus a boot warm-up and a staleness fallback) — the "stream,
 * don't assemble per request" requirement is met by serving cached bytes; the one
 * nightly build materialises ~4 MB once. Rows without a usable price or image are
 * skipped and counted (Meta rejects them anyway — better absent than broken), never
 * invented.
 */
@Service
public class MetaCatalogFeedService {

    private static final Logger logger = LoggerFactory.getLogger(MetaCatalogFeedService.class);

    static final String HEADER = "id,title,description,availability,condition,price,link,"
            + "image_link,brand,google_product_category,item_group_id,sale_price,inventory";

    private static final int PAGE_SIZE = 200;
    private static final int TITLE_MAX = 150;
    private static final int DESCRIPTION_MAX = 5000;
    private static final String DEFAULT_BRAND = "Trovemo";
    /** Staleness fallback only — the nightly refresh hook is the intended regeneration path. */
    private static final long STALE_TTL_MS = 24 * 60 * 60 * 1000L;

    private final LitemallGoodsService goodsService;
    private final LitemallGoodsProductMapper goodsProductMapper;
    private final LitemallBrandService brandService;
    private final LitemallGoodsProperties goodsProperties;
    private final PublicSiteProperties siteProperties;

    private record Snapshot(byte[] csv, long builtAt) {
    }

    private volatile Snapshot snapshot = null;
    private final ReentrantLock refreshLock = new ReentrantLock();

    public MetaCatalogFeedService(LitemallGoodsService goodsService,
                                  LitemallGoodsProductMapper goodsProductMapper,
                                  LitemallBrandService brandService,
                                  LitemallGoodsProperties goodsProperties,
                                  PublicSiteProperties siteProperties) {
        this.goodsService = goodsService;
        this.goodsProductMapper = goodsProductMapper;
        this.brandService = brandService;
        this.goodsProperties = goodsProperties;
        this.siteProperties = siteProperties;
    }

    /** Cached feed bytes; always at least the header line, never throws. */
    public byte[] feed() {
        long now = System.currentTimeMillis();
        Snapshot current = snapshot;
        if (current != null && now - current.builtAt() < STALE_TTL_MS) {
            return current.csv();
        }
        if (!refreshLock.tryLock()) {
            return current != null ? current.csv() : headerOnly();
        }
        try {
            current = snapshot;
            if (current != null && System.currentTimeMillis() - current.builtAt() < STALE_TTL_MS) {
                return current.csv();
            }
            try {
                byte[] built = build();
                snapshot = new Snapshot(built, System.currentTimeMillis());
                return built;
            } catch (RuntimeException e) {
                if (current != null) {
                    logger.warn("meta-catalog feed rebuild failed; serving previous snapshot", e);
                    return current.csv();
                }
                logger.warn("meta-catalog feed build failed with no previous snapshot; caching header-only fallback", e);
                byte[] fallback = headerOnly();
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
                feed();
            } catch (RuntimeException e) {
                logger.warn("meta-catalog feed warm-up failed; first request will retry", e);
            }
        }, "meta-catalog-warmup");
        warmer.setDaemon(true);
        warmer.start();
    }

    private byte[] build() {
        String base = normalizedBase();
        String currency = normalizedCurrency();
        Map<Integer, Integer> stockByGoods = loadStockSums();
        Map<Integer, String> brandNames = new HashMap<>();

        StringBuilder csv = new StringBuilder(1 << 22);
        csv.append(HEADER).append('\n');
        int rows = 0;
        int skippedNoPrice = 0;
        int skippedNoImage = 0;

        int page = 1;
        while (true) {
            // Page by the unique PK — same walk as SitemapService (and its add_time tie-break lesson).
            List<LitemallGoods> batch = goodsService.querySelective(
                    null, null, null, page, PAGE_SIZE, "id", "asc");
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (LitemallGoods goods : batch) {
                Integer id = goods.getId();
                if (id == null || !Boolean.TRUE.equals(goods.getIsOnSale())) {
                    continue;
                }
                String cleanName = HtmlText.clean(goods.getName());
                if (cleanName.isEmpty()) {
                    continue;
                }
                BigDecimal retail = goods.getRetailPrice();
                if (retail == null || retail.signum() <= 0) {
                    skippedNoPrice++;
                    continue;
                }
                String image = absolutizeImage(goods.getPicUrl(), base);
                if (image == null) {
                    skippedNoImage++;
                    continue;
                }

                // A live flash-deal swap leaves counter = pre-deal anchor above the swapped
                // retail; outside a deal counter == retail (never a fake strike-through).
                BigDecimal counter = goods.getCounterPrice();
                boolean onDeal = counter != null && counter.compareTo(retail) > 0;
                BigDecimal price = onDeal ? counter : retail;
                BigDecimal salePrice = onDeal ? retail : null;

                String title = HtmlText.truncateAtWord(HtmlText.uncapsIfShouty(cleanName), TITLE_MAX);
                String description = HtmlText.clean(goods.getBrief());
                if (description.isEmpty()) {
                    description = title;
                }
                description = HtmlText.truncateAtWord(description, DESCRIPTION_MAX);

                int stock = Math.max(0, stockByGoods.getOrDefault(id, 0));

                appendRow(csv,
                        String.valueOf(id),
                        title,
                        description,
                        stock > 0 ? "in stock" : "out of stock",
                        "new",
                        money(price, currency),
                        base + SeoSlugger.productPath(id, goods.getName()),
                        image,
                        brandName(goods.getBrandId(), brandNames),
                        "",
                        String.valueOf(id),
                        salePrice != null ? money(salePrice, currency) : "",
                        String.valueOf(stock));
                rows++;
            }
            if (batch.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        logger.info("meta-catalog feed rebuilt: {} rows ({} skipped no-price, {} skipped no-image), {} bytes",
                rows, skippedNoPrice, skippedNoImage, bytes.length);
        return bytes;
    }

    private byte[] headerOnly() {
        return (HEADER + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private Map<Integer, Integer> loadStockSums() {
        LitemallGoodsProductExample example = new LitemallGoodsProductExample();
        // Bind the literal — andLogicalDeleted() is inverted repo-wide, do not call it.
        example.or().andDeletedEqualTo(false);
        List<LitemallGoodsProduct> products = goodsProductMapper.selectByExample(example);
        Map<Integer, Integer> sums = new HashMap<>();
        for (LitemallGoodsProduct product : products) {
            Integer goodsId = product.getGoodsId();
            Integer number = product.getNumber();
            if (goodsId == null || number == null || number <= 0) {
                continue;
            }
            sums.merge(goodsId, number, Integer::sum);
        }
        return sums;
    }

    private String brandName(Integer brandId, Map<Integer, String> cache) {
        if (brandId == null || brandId <= 0) {
            return DEFAULT_BRAND;
        }
        return cache.computeIfAbsent(brandId, id -> {
            LitemallBrand brand = brandService.findById(id);
            String name = brand != null ? HtmlText.clean(brand.getName()) : "";
            return name.isEmpty() ? DEFAULT_BRAND : name;
        });
    }

    /**
     * Meta fetches images itself, so links must be public and absolute. CJ-hosted pics
     * are minted onto the edge's {@code /_cdn} proxy (Cloudflare-cached, our origin);
     * other absolute hosts pass through; anything unusable yields null (row skipped).
     */
    private static String absolutizeImage(String picUrl, String base) {
        if (picUrl == null || picUrl.isBlank()) {
            return null;
        }
        String pic = picUrl.trim();
        for (String[] swap : new String[][] {
                {"https://cf.cjdropshipping.com/", "/_cdn/cf/"},
                {"http://cf.cjdropshipping.com/", "/_cdn/cf/"},
                {"https://oss-cf.cjdropshipping.com/", "/_cdn/oss/"},
                {"http://oss-cf.cjdropshipping.com/", "/_cdn/oss/"}}) {
            if (pic.startsWith(swap[0])) {
                return base + swap[1] + pic.substring(swap[0].length());
            }
        }
        if (pic.startsWith("/")) {
            return base + pic;
        }
        if (pic.startsWith("https://") || pic.startsWith("http://")) {
            return pic;
        }
        return null;
    }

    private static String money(BigDecimal amount, String currency) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + currency;
    }

    private static void appendRow(StringBuilder csv, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(csvField(fields[i]));
        }
        csv.append('\n');
    }

    /** RFC 4180: quote fields containing comma/quote, escape {@code "} as {@code ""}. */
    private static String csvField(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String v = value.replaceAll("[\\u0000-\\u001F\\u007F]", " ");
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    private String normalizedCurrency() {
        String currency = goodsProperties.getCurrency();
        return currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizedBase() {
        String base = siteProperties.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            return "";
        }
        base = base.trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}