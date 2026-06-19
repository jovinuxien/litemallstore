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
     * Category fetch plan: which CJ categories to index and how many products from each, fetched
     * before indexing. {@code category} is a CJ first/second/third-level name (resolved to leaf
     * category ids via {@code getCategory}); alternatively set {@code categoryId} to a CJ leaf UUID
     * directly. {@code limit} caps the products indexed per target. Empty → legacy single-page fetch.
     */
    private List<CatalogTarget> catalogTargets = new ArrayList<>();

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

    /**
     * Retail = CJ wholesale {@code sellPrice} (USD) × {@code usdToCny} × {@code margin}, indexed in the
     * local price basis (CNY) — so CJ products sort/compare sanely against local retail prices rather
     * than being indexed at raw wholesale cost. Assumptions are configurable + profile-overridable.
     */
    /** One entry of the {@link #catalogTargets} fetch plan. */
    @Data
    public static class CatalogTarget {
        /** CJ category name (any level); resolved to leaf category ids via {@code getCategory}. */
        private String category;
        /** Optional explicit CJ leaf category UUID; when set it wins over {@link #category}. */
        private String categoryId;
        /** Max products to index from this target. */
        private int limit = 200;
    }

    @Data
    public static class Pricing {
        private BigDecimal margin = new BigDecimal("2.0");
        private BigDecimal usdToCny = new BigDecimal("7.2");
    }


    @Data
    public static class Api {
        private Auth auth;
        private Category category;
        private Product product;
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

}
