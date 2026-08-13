package org.linlinjava.litemall.goods.application.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.linlinjava.litemall.goods.application.attribution.AttributionProvider;
import org.linlinjava.litemall.goods.application.inventoryflow.InventoryFlowGateway;
import org.springframework.beans.factory.ObjectProvider;
import org.linlinjava.litemall.goods.infrastructure.acl.adapter.CjProductToNativeAdapter;
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
    private final CjPricing pricing;
    private final ObjectProvider<InventoryFlowGateway> flowGateway;

    public CjDetailEnrichmentService(CJProductService cjProductService,
                                     LitemallCjProductService cjProductStore,
                                     CjProductPromotionService promotionService,
                                     SearchReindexService reindexService,
                                     CJDropshippingConfig config,
                                     ObjectMapper objectMapper,
                                     CjPricing pricing,
                                     ObjectProvider<InventoryFlowGateway> flowGateway) {
        this.cjProductService = cjProductService;
        this.cjProductStore = cjProductStore;
        this.promotionService = promotionService;
        this.reindexService = reindexService;
        this.config = config;
        this.objectMapper = objectMapper;
        this.pricing = pricing;
        this.flowGateway = flowGateway;
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
        logSupplierCoverage(rows);
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

        // Wave 12: refresh the product-level raw cost from detail (exact, not the list range's
        // lower bound); null preserves the sync-landed value (enrich statement COALESCEs, and the
        // fallback variant below reads the freshest value off the row). The RETAIL is recomputed
        // with it: the nightly plan only rotates a slice of the catalog, so for the long tail
        // THIS is the repricing path — without it an on-demand-enriched product captures its
        // cost but keeps the pre-Wave-12 ×14.4 price until a sync happens to list it again.
        // Wave 14: the effective margin is per-category (L1 override else global), resolved once
        // per product off the snapshot row's CJ leaf UUID.
        BigDecimal effMargin = pricing.marginForCjLeaf(firstCategoryId(row.getCategoryIds()));
        BigDecimal detailCost = pricing.cost(d.getSellPrice());
        if (detailCost != null) {
            row.setSellPrice(detailCost);
            row.setPrice(pricing.retail(detailCost, effMargin));
        } else if (row.getSellPrice() != null) {
            row.setPrice(pricing.retail(row.getSellPrice(), effMargin));
        }

        // Real per-SKU variants: variant_sell_price (raw USD cost, Wave 12) + variant_price
        // (its marked-up retail) + real stock.
        List<Map<String, Object>> variantMaps = new ArrayList<>();
        for (CJProductVariantData v : variants) {
            Map<String, Object> vm = new LinkedHashMap<>();
            vm.put("vid", v.getVid());
            vm.put("variant_sku", v.getVariantSku());
            vm.put("options", variantOptions(v));
            BigDecimal vCost = pricing.cost(v.getVariantSellPrice());
            if (vCost != null) {
                vm.put("variant_sell_price", vCost);
                vm.put("variant_price", pricing.retail(vCost, effMargin));
            }
            // Wave 25.1: capture the per-variant image (key present only when usable — plain URL
            // on a /_cdn-covered host, column-width safe) so promote can land it on the SKU url
            // and the already-live SPA half starts switching the PDP photo per variant.
            String variantImage = variantImageOf(v.getVariantImage());
            if (variantImage != null) {
                vm.put("variant_image", variantImage);
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
            if (row.getSellPrice() != null) {
                vm.put("variant_sell_price", row.getSellPrice());
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

        // V60 supplier attribution: persist the raw CJ supplier identity (sparse — populated for
        // roughly half of items in the 2026-08-09 live probe). Nulls preserve prior values via the
        // enrich statement's COALESCE. The name is a raw legal-entity string; it only ever reaches
        // customers after an admin curates the derived brand row (display_enabled gate).
        // Wave 25.1: CJ sometimes delivers literal junk ("{}") in these fields — junk reads as
        // ABSENT so it never lands on the row (and never inflates the coverage probe).
        row.setSupplierId(usableOrNull(trimTo(readable(d.getSupplierId()), 63)));
        row.setSupplierName(usableOrNull(trimTo(readable(d.getSupplierName()), 255)));

        cjProductStore.enrich(row);                  // persist enriched snapshot + stamp enriched_time
        // Land the freshly-enriched row into the native litemall_goods family and index THAT (OCS
        // single-source, Phase 4) — no more parallel cj_<pid> document. promote() commits in its own
        // transaction, so the subsequent reindex reads the committed native goods.
        Integer goodsId = promotionService.promote(row);
        reindexService.reindexGoods(goodsId);
        // Wave 12: every enrichment path (nightly batch, admin targeted, on-demand) funnels
        // through here — hand the re-promoted pid to the inventory flow (metric refresh with the
        // now-real per-variant costs/stock). Async, and never allowed to break enrichment.
        try {
            InventoryFlowGateway gateway = flowGateway.getIfAvailable();
            if (gateway != null) {
                gateway.onEnrichmentBatch(List.of(pid));
            }
        } catch (RuntimeException flowEx) {
            LOGGER.warn("inventory flow hand-off failed for pid {} (enrichment continues): {}",
                    pid, flowEx.getMessage());
        }
        return goodsId;
    }

    /**
     * Wave-25 coverage probe: how often CJ's detail response actually carries a supplier identity —
     * per batch (from the rows just processed) and cumulatively over every enriched row (one cheap
     * count query). Drives the go/no-go on leaning harder on supplier attribution; log-only.
     */
    private void logSupplierCoverage(List<LitemallCjProduct> batchRows) {
        try {
            int batchPopulated = 0;
            for (LitemallCjProduct r : batchRows) {
                if (r.getSupplierId() != null && !r.getSupplierId().isBlank()) {
                    batchPopulated++;
                }
            }
            Map<String, Object> cov = cjProductStore.supplierCoverage();
            long total = ((Number) cov.getOrDefault("enrichedTotal", 0)).longValue();
            long populated = ((Number) cov.getOrDefault("supplierPopulated", 0)).longValue();
            String pct = total > 0 ? String.format("%.1f%%", populated * 100.0 / total) : "n/a";
            LOGGER.info("CJ supplier attribution coverage: batch {}/{} populated; cumulative {}/{} enriched rows ({})",
                    batchPopulated, batchRows.size(), populated, total, pct);
        } catch (RuntimeException ex) {
            LOGGER.warn("CJ supplier coverage probe failed (enrichment unaffected): {}", ex.getMessage());
        }
    }

    private String trimTo(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String t = value.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    /** Wave 25.1: junk-tolerant identity pass-through — see {@link AttributionProvider#usableIdentityField}. */
    private String usableOrNull(String value) {
        return AttributionProvider.usableIdentityField(value) ? value : null;
    }

    /**
     * Wave 25.1: normalize CJ's {@code variantImage} (tolerating a JSON-array-string shape, like
     * {@code productImage}) and keep it only when the promote path can actually use it — same
     * host/width rules as {@link CjProductToNativeAdapter#usableVariantImage}.
     */
    private String variantImageOf(String raw) {
        List<String> urls = imagesOf(raw);
        return urls.isEmpty() ? null : CjProductToNativeAdapter.usableVariantImage(urls.get(0));
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
        // Wave 25.1: a junk supplier value ("{}") must not render as a PDP attribute.
        putAttr(attrs, "Supplier", usableOrNull(readable(d.getSupplierName())));
        return attrs;
    }

    private void putAttr(Map<String, Object> attrs, String name, String value) {
        String readable = readable(value);
        if (readable != null && !readable.isBlank()) {
            attrs.put(name, readable);
        }
    }

    /** First CJ leaf category UUID off a snapshot row's category_ids JSON array; null when absent. */
    private String firstCategoryId(String categoryIdsJson) {
        if (categoryIdsJson == null || categoryIdsJson.isBlank()) {
            return null;
        }
        try {
            String[] ids = objectMapper.readValue(categoryIdsJson, String[].class);
            for (String id : ids) {
                if (id != null && !id.isBlank()) {
                    return id.trim();
                }
            }
        } catch (Exception e) {
            // fall through — global margin applies
        }
        return null;
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
