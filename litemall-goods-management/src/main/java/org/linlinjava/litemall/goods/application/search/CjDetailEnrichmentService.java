package org.linlinjava.litemall.goods.application.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.inventory.CJInventoryData;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productdetail.CJProductDetailData;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productreview.CJProductComment;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productreview.CJProductReviewData;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productvariant.CJProductVariantData;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Second-stage CJ snapshot enrichment: turns the SHALLOW catalog rows that {@link CjSnapshotSyncService}
 * lands (single fake-stock variant, no attributes) into FULL rows carrying real per-SKU prices, real
 * warehouse inventory, gallery images and descriptive attributes — so the OCS facet/price-range/
 * in-stock-scoring machinery acts on CJ products the same as on local goods, and the CJ detail page can
 * be served straight from the DB (no live CJ call).
 *
 * <p>Pipeline discipline is preserved: this reads CJ {@code product/query} detail + {@code product/stock/
 * queryByVid} inventory THROUGH the Redis staging buffer ({@link CJProductService}), writes the result
 * into {@code litemall_cj_product} (the enriched snapshot), then promotes the row into the native
 * {@code litemall_goods} family ({@code CjProductPromotionService}) and indexes THAT native goods —
 * OCS reads the DB only (Phase 4), never a parallel {@code cj_<pid>} document.
 *
 * <p>CJ inventory is keyed by VARIANT, so a product costs 1 detail + N inventory calls. Against CJ's
 * daily request quota this CANNOT run over the whole catalog in one shot, so enrichment is incremental:
 * each run takes a capped batch of the least-recently-enriched rows ({@code enriched_time} NULL first),
 * paced by {@link CJProductService}'s rate limiter, and converges over successive runs.
 */
@Service
public class CjDetailEnrichmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CjDetailEnrichmentService.class);

    /** Reviews sampled per product for the rating average (page 1); total gives the count. */
    private static final int REVIEW_SAMPLE_SIZE = 20;

    private final CJProductService cjProductService;
    private final LitemallCjProductService cjProductStore;
    private final CjProductPromotionService promotionService;
    private final SearchReindexService reindexService;
    private final CJDropshippingConfig config;
    private final ObjectMapper objectMapper;

    public CjDetailEnrichmentService(CJProductService cjProductService,
                                     LitemallCjProductService cjProductStore,
                                     CjProductPromotionService promotionService,
                                     SearchReindexService reindexService,
                                     CJDropshippingConfig config,
                                     ObjectMapper objectMapper) {
        this.cjProductService = cjProductService;
        this.cjProductStore = cjProductStore;
        this.promotionService = promotionService;
        this.reindexService = reindexService;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /** Outcome of one enrichment run. */
    public record EnrichResult(int enriched, int failed) {
        public static EnrichResult empty() {
            return new EnrichResult(0, 0);
        }
    }

    /**
     * Enrich up to {@code batchSize} of the least-recently-enriched live CJ rows: fetch detail +
     * per-variant inventory (paced, via Redis), persist real variants/attributes/images, then reindex.
     * A per-row failure is logged and skipped (that row is retried next run); CJ-disabled is a no-op.
     */
    public EnrichResult enrichBatch(int batchSize) {
        if (!config.isEnabled() || batchSize <= 0) {
            return EnrichResult.empty();
        }
        List<LitemallCjProduct> rows = cjProductStore.queryForEnrichment(batchSize);
        int enriched = 0;
        int failed = 0;
        for (LitemallCjProduct row : rows) {
            try {
                enrichOne(row);
                enriched++;
            } catch (RuntimeException ex) {
                LOGGER.warn("CJ enrichment skipped pid={}: {}", row.getPid(), ex.getMessage());
                failed++;
            }
        }
        LOGGER.info("CJ detail+inventory enrichment: {} enriched, {} failed (batch requested {}, due {})",
                enriched, failed, batchSize, rows.size());
        return new EnrichResult(enriched, failed);
    }

    /** Outcome of a single-pid enrichment run: the native goods id the row promoted to, or null when skipped. */
    public record EnrichOneResult(String pid, Integer goodsId) {
    }

    /**
     * Enrich ONE CJ product by its raw {@code pid}, on demand — the targeted counterpart to
     * {@link #enrichBatch(int)} for unblocking a specific product whose SKUs still carry no
     * {@code cj_vid} (so it can't be ordered at CJ) without draining the whole least-recently-enriched
     * queue. Fetches CJ detail + per-variant inventory (paced, via Redis), persists the real variants,
     * re-promotes the row into native goods (filling {@code litemall_goods_product.cj_vid}) and reindexes.
     *
     * @throws IllegalArgumentException if CJ is disabled, the pid is blank, or no live snapshot row exists
     *         for it (so the admin sees a clear 4xx rather than a silent no-op)
     */
    public EnrichOneResult enrichByPid(String pid) {
        if (!config.isEnabled()) {
            throw new IllegalArgumentException("CJ Dropshipping is disabled; cannot enrich pid " + pid);
        }
        if (pid == null || pid.isBlank()) {
            throw new IllegalArgumentException("pid is required");
        }
        LitemallCjProduct row = cjProductStore.findByPid(pid);
        if (row == null) {
            throw new IllegalArgumentException("no live CJ snapshot row for pid " + pid);
        }
        Integer goodsId = enrichOne(row);
        LOGGER.info("CJ targeted enrichment: pid={} enriched -> native goods {}", pid, goodsId);
        return new EnrichOneResult(pid, goodsId);
    }

    private Integer enrichOne(LitemallCjProduct row) {
        String pid = row.getPid();
        CJProductDetailData d = cjProductService.getProductDetail(pid);
        if (d == null) {
            throw new RuntimeException("no CJ detail for pid " + pid);
        }
        List<CJProductVariantData> variants = d.getVariants() != null ? d.getVariants() : List.of();

        // Real per-SKU variants: variant_price (retail of the variant's wholesale) + real stock.
        List<Map<String, Object>> variantMaps = new ArrayList<>();
        for (CJProductVariantData v : variants) {
            Map<String, Object> vm = new LinkedHashMap<>();
            vm.put("vid", v.getVid());
            vm.put("variant_sku", v.getVariantSku());
            vm.put("options", variantOptions(v));
            BigDecimal vp = retail(v.getVariantSellPrice());
            if (vp != null) {
                vm.put("variant_price", vp);
            }
            vm.put("stock", stockOf(v.getVid()));
            variantMaps.add(vm);
        }
        if (variantMaps.isEmpty()) {
            // No variants returned — keep a single default-stock variant at product retail so the
            // ln1p(stock) scoring doesn't sink the product to zero.
            Map<String, Object> vm = new LinkedHashMap<>();
            vm.put("stock", config.getDefaultStock());
            if (row.getPrice() != null) {
                vm.put("variant_price", row.getPrice());
            }
            variantMaps.add(vm);
        }

        row.setVariantsJson(writeJson(variantMaps));
        row.setAttributesJson(writeJson(attributesOf(d)));
        row.setImagesJson(writeJson(imagesOf(d.getProductImage())));
        // Full rich product description (HTML) for the DB-served detail page — the snapshot list only
        // carried a short brief (remark/title); `description` stays that brief (OCS/search), detail_html
        // gets the full text so the page renders it (mirrors local goods.detail vs goods.brief).
        String fullDescription = d.getDescription();
        if (fullDescription != null && !fullDescription.isBlank()) {
            row.setDetailHtml(fullDescription);
        }
        // CJ has no was/now strike-through price (suggestSellPrice is a recommendation, not a discount),
        // so discount_price stays null — leaving it real rather than fabricating a markdown.
        // PARKED (2026-07-16): a suggest_price organic-anchor design (V40 column, still in the DB)
        // shipped and was reverted the same day pending an efficiency redesign — see
        // doc/cj-deals-strategy-2026-07-16.pdf and commit a82a19e0e.
        row.setDiscountPrice(null);

        // V31 ranking signals. listedNum (popularity) + createTime (true creation date) ride the
        // detail response we already fetched — free. The review aggregate needs one more paced CJ
        // call, folded into THIS per-product loop so it shares the 1-QPS budget + Redis cache and
        // converges over the same nightly enrichment cursor (no separate sweep).
        row.setListedNum(d.getListedNum());
        row.setCjCreateTime(parseCjDateTime(d.getCreateTime()));
        applyReviewAggregate(row, pid);

        cjProductStore.enrich(row);                  // persist enriched snapshot + stamp enriched_time
        // Land the freshly-enriched row into the native litemall_goods family and index THAT (OCS
        // single-source, Phase 4) — no more parallel cj_<pid> document. promote() commits in its own
        // transaction, so the subsequent reindex reads the committed native goods.
        Integer goodsId = promotionService.promote(row);
        reindexService.reindexGoods(goodsId);
        return goodsId;
    }

    /**
     * Real stock for a variant = sum of warehouse {@code storageNum} (a genuinely out-of-stock variant
     * with zero across warehouses correctly sums to 0 → buried by in-stock scoring). An EMPTY inventory
     * response (API error / no warehouse data — indistinguishable from "unknown") falls back to the
     * configured default so a transient failure doesn't wrongly zero the product.
     */
    private int stockOf(String vid) {
        List<CJInventoryData> inv = cjProductService.getInventory(vid);
        if (inv == null || inv.isEmpty()) {
            return config.getDefaultStock();
        }
        int sum = 0;
        boolean any = false;
        for (CJInventoryData area : inv) {
            if (area.getStorageNum() != null) {
                sum += area.getStorageNum();
                any = true;
            }
        }
        return any ? sum : config.getDefaultStock();
    }

    /** Retail = wholesale USD × usdToCny × margin in the local CNY basis; null on no cost. */
    private BigDecimal retail(Double sellPrice) {
        if (sellPrice == null) {
            return null;
        }
        CJDropshippingConfig.Pricing pricing = config.getPricing();
        return BigDecimal.valueOf(sellPrice)
                .multiply(pricing.getUsdToCny())
                .multiply(pricing.getMargin())
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Fetch the CJ review aggregate ({@code total} = review count, average {@code score} = rating)
     * and stamp it on the row. One paced CJ call (page 1); failures/absent reviews leave the
     * signals null so the {@code enrich} update's COALESCE preserves any prior value. reviews_synced_time
     * is stamped only when the call actually returned, so a failed fetch stays due on the next cursor.
     */
    private void applyReviewAggregate(LitemallCjProduct row, String pid) {
        CJProductReviewData reviews;
        try {
            reviews = cjProductService.getProductComments(pid, 1, REVIEW_SAMPLE_SIZE);
        } catch (RuntimeException ex) {
            LOGGER.warn("CJ review fetch failed for pid={}: {}", pid, ex.getMessage());
            return;
        }
        if (reviews == null) {
            return;
        }
        row.setReviewCount(parseIntOrZero(reviews.getTotal()));
        row.setRating(averageScore(reviews.getList()));
        row.setReviewsSyncedTime(LocalDateTime.now());
    }

    private Integer parseIntOrZero(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Mean of the sampled review scores (1..5), rounded to 1 decimal; 0.0 when none parse. */
    private BigDecimal averageScore(List<CJProductComment> list) {
        if (list == null || list.isEmpty()) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        int sum = 0;
        int n = 0;
        for (CJProductComment c : list) {
            Integer s = parseScore(c.getScore());
            if (s != null) {
                sum += s;
                n++;
            }
        }
        if (n == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(n), 1, RoundingMode.HALF_UP);
    }

    private Integer parseScore(String score) {
        if (score == null || score.isBlank()) {
            return null;
        }
        try {
            int s = (int) Math.round(Double.parseDouble(score.trim()));
            return (s >= 1 && s <= 5) ? s : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Tolerant CJ timestamp parse (ISO {@code T} or {@code "yyyy-MM-dd HH:mm:ss"}); null on failure. */
    private LocalDateTime parseCjDateTime(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        String v = date.trim();
        try {
            return LocalDateTime.parse(v.replace(' ', 'T'));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Parse a CJ {@code variantKey} (e.g. {@code ["Black","XL"]}) into its option-value strings. */
    private List<String> variantOptions(CJProductVariantData v) {
        String key = v.getVariantKey();
        if (key != null && !key.isBlank()) {
            try {
                return List.of(objectMapper.readValue(key, String[].class));
            } catch (Exception ignore) {
                // fall through
            }
        }
        String name = v.getVariantNameEn() != null ? v.getVariantNameEn() : v.getVariantName();
        return name != null ? List.of(name) : List.of();
    }

    /**
     * Descriptive attributes for the DB-served detail page. The OCS index filters this map down to the
     * facetable allow-list ({@code litemall.search.facet-attributes}) at index time, so storing the full
     * set here costs nothing in the facets ({@code Material}/{@code Weight} match the allow-list;
     * {@code Unit}/{@code Supplier} render on the page only).
     */
    private Map<String, Object> attributesOf(CJProductDetailData d) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        putAttr(attrs, "Material", d.getMaterialNameEn() != null ? d.getMaterialNameEn() : d.getMaterialName());
        putAttr(attrs, "Weight", d.getProductWeight());
        putAttr(attrs, "Unit", d.getProductUnit());
        putAttr(attrs, "Supplier", d.getSupplierName());
        return attrs;
    }

    private void putAttr(Map<String, Object> attrs, String name, String value) {
        String readable = readable(value);
        if (readable != null && !readable.isBlank()) {
            attrs.put(name, readable);
        }
    }

    /** CJ {@code productImage} may be a plain URL or a JSON-stringified array; return a clean URL list. */
    private List<String> imagesOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String t = raw.trim();
        if (t.startsWith("[")) {
            try {
                List<String> out = new ArrayList<>();
                for (String u : objectMapper.readValue(t, String[].class)) {
                    if (u != null && !u.isBlank()) {
                        out.add(u.trim());
                    }
                }
                return out;
            } catch (Exception ignore) {
                // fall through to the raw value
            }
        }
        return List.of(t);
    }

    /** Flatten a possibly JSON-stringified CJ field ({@code "[\"Cloth\"]"}) to a readable string. */
    private String readable(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.startsWith("[")) {
            try {
                return String.join(", ", objectMapper.readValue(t, String[].class));
            } catch (Exception ignore) {
                // fall through
            }
        }
        return t;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            LOGGER.warn("Failed to serialize CJ enrichment field: {}", ex.getMessage());
            return null;
        }
    }
}
