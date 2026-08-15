package org.linlinjava.litemall.goods.infrastructure.configuration;

import lombok.Data;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


@Component
@Data
@ConfigurationProperties(prefix = "spring.cjdropship")
public class CJDropshippingConfig {

    private Api api;

    /** Master switch for indexing CJ products into OCS; off keeps the index local-only. */
    private boolean enabled = true;

    /** Retail-price derivation from CJ wholesale {@code sellPrice}. */
    private Pricing pricing = new Pricing();

    /** Stock assumed for a CJ SKU until real inventory is fetched (so in-stock scoring won't bury it). */
    private int defaultStock = 100;

    /**
     * Spring cron (sec min hour dom mon dow) for the nightly CJ catalog indexing job
     * ({@code CjCatalogRefreshTask}). Defaults to 03:00 daily.
     */
    private String refreshCron = "0 0 3 * * *";

    /**
     * Seconds to block between consecutive CJ {@code /product/list} calls, matching the CJ quota.
     * The paced nightly job waits this long between upstream requests so a multi-category plan
     * stays within the rate limit.
     */
    private int fetchPaceSeconds = 300;
    /**
     * Attempts per /product/list page before a category is abandoned short. CJ QPS-rejects a
     * substantial share of calls even at the configured pace (~17% measured 2026-08-13), and the
     * old break-on-first-failure made every nightly run silently lossy. Retries ride the same
     * paced limiter, so raising this costs wall-clock, never a QPS burst.
     */
    private int fetchRetries = 3;

    /** CJ {@code /product/list} page size (CJ caps this at ~200). */
    private int pageSize = 200;

    /**
     * Run a one-shot CJ catalog refresh shortly after startup (in addition to the nightly cron), so a
     * freshly deployed/restarted service repopulates the CJ catalog without waiting for the cron or an
     * authenticated reindex. Set false to rely solely on the cron.
     */
    private boolean refreshOnStartup = true;

    /** Delay (ms) after the app is ready before the one-shot startup refresh fires. Default 10 min. */
    private long refreshStartupDelayMs = 600000;

    /**
     * Erosion tripwire for stale-pruning: a full sync may soft-delete at most this fraction of the
     * previously-live snapshot in one run. CJ's per-category listings rotate and page-limited hauls
     * see only a slice of a big category, so "absent from this fetch" is weak evidence of upstream
     * delisting — a prune bigger than this is almost certainly a partial fetch, and is skipped with
     * an ERROR log instead of executed (the incident on 07-05..07-10 eroded 7.7k of 7.8k products).
     * Raise deliberately (e.g. to 1.0) for an intentional catalog rebuild.
     */
    private double pruneMaxFraction = 0.2;

    /**
     * Spring cron for the incremental CJ detail+inventory enrichment job ({@code CjDetailEnrichmentService}).
     * Runs after the list sync (default 03:30 daily). Each enriched product costs 1 detail + N inventory
     * calls, so enrichment is intentionally incremental — see {@link #enrichBatchSize}.
     */
    private String enrichCron = "0 30 3 * * *";

    /**
     * Number of CJ products enriched per run. Enrichment fetches detail (1 call) + per-variant
     * inventory (N calls) per product against CJ's daily request quota — a batch of N products is
     * roughly N×(1+avgVariants) CJ calls. The job converges over successive runs via the
     * {@code enriched_time} cursor.
     *
     * <p>Wave 26 raised this from 100 (the storefront narrowed to ~2.2k goods with ~1k lacking a
     * detail body, and at 100/night that gap drained in ~11 nights), then settled at 200. Sized against the MEASURED CJ points budget (2026-08-15), not a guess:
     * ~280 points per product (1 detail + 4.6 variant-inventory calls on average), a ~69k daily
     * budget that CJ scales with ORDER volume rather than catalogue size, and ~3.4k/night for the
     * anchor-only sync. 200 x 280 = ~56k, leaving headroom for order placement, which draws on the
     * SAME budget. 400 would need ~112k — 1.6x the whole day, and the day it was tried the budget
     * ran out mid-afternoon.
     *
     * <p>⚠ CJ daily points are shared ACCOUNT-WIDE with ORDER PLACEMENT. What makes a raised value
     * safe is that {@code CjDetailEnrichmentService.enrichBatch} abandons the batch on quota
     * exhaustion or repeated failure instead of grinding on. Env: {@code
     * LITEMALL_CJ_ENRICH_BATCH_SIZE} (0 disables enrichment).
     */
    private int enrichBatchSize = 200;

    /**
     * Demand-Driven CJ Enrichment: viewing a shallow CJ goods (customer detail page, or the
     * order service's goods fetch — incl. the submit-block retry) enqueues an async
     * enrich-in-place, so quota is spent in demand order. Disable to fall back to
     * batch-only enrichment.
     */
    private boolean onDemandEnrichEnabled = true;

    /** Per-product cooldown between on-demand enrichment attempts (default 6 h = raw-cache TTL). */
    private long onDemandCooldownSeconds = 21600;

    /** Bounded on-demand queue; bursts beyond it are dropped with a log (nightly batch catches up). */
    private int onDemandQueueCapacity = 200;

    /**
     * Category fetch plan: which CJ categories to index and how many products from each, fetched
     * before indexing. {@code category} is a CJ first/second/third-level name (resolved to leaf
     * category ids via {@code getCategory}); alternatively set {@code categoryId} to a CJ leaf UUID
     * directly. {@code limit} caps the products indexed per target. Empty → legacy single-page fetch.
     */
    private List<CatalogTarget> catalogTargets = new ArrayList<>();

    /**
     * Wave 26: day of week on which the FULL catalog plan runs (every target, with stale-prune and
     * native reconcile). On the other six days only {@link CatalogTarget#isNightly()} targets are
     * swept, additively. Blank/invalid ⇒ every day is a full run (pre-Wave-26 behaviour).
     */
    private String fullSyncDay = "SUNDAY";

    /**
     * Wave 26 Phase 1b: which warehouse country codes count as "EU stock" when the enrichment seam
     * records the per-country inventory split. Phase 1a measured DE as CJ's ONLY EU warehouse
     * (ES/CZ/IT/NL/BE/PL/SE/DK/AT and "EU" all returned zero on a 13k-product leaf), so this is a
     * list of one today — kept configurable because the day CJ opens a second EU warehouse, this
     * is the only line that should need changing. GB is deliberately absent: post-Brexit it does
     * not serve EU customers without customs.
     */
    private java.util.Set<String> euWarehouseCountries = new java.util.LinkedHashSet<>(java.util.List.of("DE"));

    public java.util.Set<String> getEuWarehouseCountries() {
        return euWarehouseCountries;
    }

    public void setEuWarehouseCountries(java.util.Set<String> euWarehouseCountries) {
        this.euWarehouseCountries = euWarehouseCountries == null
                ? new java.util.LinkedHashSet<>() : euWarehouseCountries;
    }

    public String getFullSyncDay() {
        return fullSyncDay;
    }

    public void setFullSyncDay(String fullSyncDay) {
        this.fullSyncDay = fullSyncDay;
    }

    /**
     * CJ-category → local litemall category id, as {@code "Category Name=<localId>"} entries (a List,
     * NOT a Map — Spring's relaxed binding mangles map KEYS containing spaces, e.g. "Shoulder Bags").
     * Matched case-insensitively against any segment of the CJ category path, so a CJ product folds
     * into the ONE existing local category facet. Unmapped → a virtual "Imported" bucket (no junk
     * numeric id, no DB write).
     */
    private List<String> categoryMapping = new ArrayList<>();

    /** Classpath resource of a recorded CJ product-list response, used when the live CJ API is unreachable. */
    private String samplePath;

    /** Redis staging-buffer settings for raw CJ API payloads (the fetch → Redis → DB → OCS pipeline). */
    private Redis redis = new Redis();

    /**
     * Redis is the durable staging buffer for raw CJ {@code /product/list} responses: a rate-limited
     * fetch lands payloads here (surviving restarts, so we don't re-hit the 1-request/300s CJ API),
     * and the snapshot sync reads them back to normalize → persist into {@code litemall_cj_product}.
     */
    @Data
    public static class Redis {
        /** TTL (seconds) for a cached raw CJ list page; default 6h. */
        private long rawTtlSeconds = 21600;

        /**
         * Purge the raw CJ {@code list:*} staging keys from Redis once a daily sync has safely landed
         * the data in {@code litemall_cj_product}. The DB is the durable source after a sync, so the
         * consumed raw pages are dead weight; clearing them frees Redis and forces the next cycle to
         * re-fetch fresh pages. Off → rely solely on {@link #rawTtlSeconds} expiry.
         */
        private boolean purgeAfterSync = true;
    }

    /** One entry of the {@link #catalogTargets} fetch plan. */
    @Data
    public static class CatalogTarget {
        /** CJ category name (any level); resolved to leaf category ids via {@code getCategory}. */
        private String category;
        /** Optional explicit CJ leaf category UUID; when set it wins over {@link #category}. */
        private String categoryId;
        /** Max products to index from this target. */
        private int limit = 200;
        /**
         * When &gt; 0, every leaf category this target resolves to gets its OWN budget of this many
         * products (a broad L1 target then fills ALL its leaves instead of the first leaf consuming
         * the whole {@link #limit}); {@link #limit} &gt; 0 acts only as an optional overall cap.
         * 0 keeps the legacy shared-budget behavior.
         */
        private int perLeafLimit = 0;
        /**
         * Wave 26 Phase 2: optional CJ-side sourcing filters for this target. Absent ⇒ requests are
         * byte-identical to today. {@code countryCode} is ONE code (max 4 chars — CJ rejects lists);
         * DE is the only EU warehouse CJ stocks. ⚠ The price bounds are on CJ COST, not our retail:
         * at margin 2.5 a €25–80 retail band is roughly €10–32 of cost.
         */
        private String countryCode;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        /**
         * Wave 26: does this target run on the nightly sweep, or only on the weekly full sync?
         * Default TRUE = every night, which is the pre-Wave-26 behaviour.
         *
         * <p>Why it exists: CJ's daily API points are a hard, shared budget (~69k, and CJ scales it
         * with ORDER volume, not catalogue size). Measured 2026-08-15: the nightly sweep over all
         * ~540 leaves costs ~27k of it — 40% of the day — to mirror 12 categories the storefront no
         * longer sells, starving both enrichment and ORDER PLACEMENT, which share the same budget.
         *
         * <p>Setting this false on the non-anchor targets keeps them mirrored (weekly, so the
         * catalogue stays reversible — "pipeline broad, storefront narrow" still holds) while the
         * anchor refreshes nightly. ⚠ A nightly subset runs as a TARGETED sync: additive only, no
         * stale-prune and no native reconcile, because both would judge the whole catalogue against
         * a slice. Pruning happens on the weekly full run.
         */
        private boolean nightly = true;

        public boolean isNightly() {
            return nightly;
        }

        public void setNightly(boolean nightly) {
            this.nightly = nightly;
        }

        /** Validated filter for this target; {@link CjListFilter#NONE} when nothing is configured. */
        public CjListFilter toFilter() {
            return new CjListFilter(countryCode, minPrice, maxPrice);
        }
    }

    /**
     * Retail = CJ wholesale cost (USD) × {@code margin}. Wave 12 (user decision 2026-07-28):
     * margin is 1.25 and the old {@code usdToCny} 7.2 currency factor was DELETED outright —
     * prices live in the USD basis, and the raw cost is persisted alongside
     * ({@code litemall_cj_product.sell_price}, {@code litemall_goods[_product].cost}).
     */
    @Data
    public static class Pricing {
        private BigDecimal margin = new BigDecimal("1.25");
    }


    @Data
    public static class Api {
        private Auth auth;
        private Category category;
        private Product product;
        private Warehouse warehouse;
    }


    @Data
    public static class Auth {
        private String cjEmail;
        private String cjApiKey;
        private String accessUrl;
        private String refreshUrl;
    }

    @Data
    public static class Category {
        private String categoryListUrl;
    }

    @Data
    public static class Product {
        private String listUrl;
        private String productDetailUrl;
        /** CJ {@code product/stock/queryByVid} endpoint — per-variant warehouse inventory. */
        private String stockQueryUrl;
        /** CJ {@code product/productComments} endpoint — per-product customer reviews. */
        private String commentsUrl;
        /** CJ {@code product/sourcing/create} endpoint — create a product-sourcing request. */
        private String sourcingCreateUrl;
        /** CJ {@code product/sourcing/query} endpoint — sourcing-request status by sourceIds. */
        private String sourcingQueryUrl;
        /** CJ {@code product/queryVideosByProductId} endpoint — per-product videos. */
        private String productVideosUrl;
    }

    @Data
    public static class Warehouse {
        /** CJ {@code warehouse/detail} endpoint — storage info by {@code ?id=<storageId>}. */
        private String detailUrl;
    }

    // Helper method for easy access to commonly used properties
    public String getCjEmail(){
       return api != null && api.getAuth()!= null ? api.getAuth().getCjEmail() : null;
    }
    public String getCjApiKey(){
       return api != null && api.getAuth()!= null ? api.getAuth().getCjApiKey() : null;
    }

    public String getAccessUrl(){
       return api != null && api.getAuth()!= null ? api.getAuth().getAccessUrl() : null;
    }

    public String getRefreshUrl(){
       return api != null && api.getAuth()!= null ? api.getAuth().getRefreshUrl() : null;
    }

    public String getCategoryListUrl(){
       return api != null && api.getCategory()!= null ? api.getCategory().getCategoryListUrl() : null;
    }

    public String getProductListUrl(){
       return api != null && api.getProduct()!= null ? api.getProduct().getListUrl() : null;
    }

    public String getProductDetailUrl(){
       return api != null && api.getProduct()!= null ? api.getProduct().getProductDetailUrl() : null;
    }

    public String getStockQueryUrl(){
       return api != null && api.getProduct()!= null ? api.getProduct().getStockQueryUrl() : null;
    }

    public String getProductCommentsUrl(){
       return api != null && api.getProduct()!= null ? api.getProduct().getCommentsUrl() : null;
    }

    public String getSourcingCreateUrl(){
       return api != null && api.getProduct()!= null ? api.getProduct().getSourcingCreateUrl() : null;
    }

    public String getSourcingQueryUrl(){
       return api != null && api.getProduct()!= null ? api.getProduct().getSourcingQueryUrl() : null;
    }

    public String getProductVideosUrl(){
       return api != null && api.getProduct()!= null ? api.getProduct().getProductVideosUrl() : null;
    }

    public String getWarehouseDetailUrl(){
       return api != null && api.getWarehouse()!= null ? api.getWarehouse().getDetailUrl() : null;
    }

}
