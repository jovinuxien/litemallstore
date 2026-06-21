package org.linlinjava.litemall.goods.application.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.linlinjava.litemall.goods.domain.service.elastic.CjProductIndexingService;
import org.linlinjava.litemall.goods.infrastructure.acl.cache.CjRawCacheRepository;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.cjcategory.CJCategoryDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProduct;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Refreshes the {@code litemall_cj_product} snapshot — the durable, DB-backed source the OCS index
 * is built from — from the CJ Dropshipping API, via the Redis staging buffer.
 *
 * <p>Pipeline: fetch the configured {@code catalog-targets} through {@link CJProductService} (which
 * reads-through Redis and paces live calls by the CJ quota) → NORMALIZE each {@link CJProduct} into a
 * customer-ready {@link LitemallCjProduct} row (English title, retail-marked-up price, CJ-category →
 * local-category mapping, default-stock variant) → upsert. The row's primary key is the RAW CJ pid
 * (a UUID), so it can be used directly to fetch CJ detail / place a CJ order; the {@code cj_} prefix
 * is applied only later, at OCS index time.
 *
 * <p>This is the single home of CJ normalization (relocated out of {@link CjProductIndexingService},
 * which now just maps a persisted row → {@code ProductDocument}). After upserting, pids no longer
 * present upstream are soft-deleted and returned so the caller can drop their {@code cj_<pid>} docs
 * from OCS — closing the stale-deletion gap the index-only design could not.
 */
@Service
public class CjSnapshotSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CjSnapshotSyncService.class);

    private final CJProductService cjProductService;
    private final LitemallCategoryService categoryService;
    private final LitemallCjProductService cjProductStore;
    private final CJDropshippingConfig config;
    private final ObjectMapper objectMapper;
    private final CjRawCacheRepository rawCache;

    public CjSnapshotSyncService(CJProductService cjProductService,
                                 LitemallCategoryService categoryService,
                                 LitemallCjProductService cjProductStore,
                                 CJDropshippingConfig config,
                                 ObjectMapper objectMapper,
                                 CjRawCacheRepository rawCache) {
        this.cjProductService = cjProductService;
        this.categoryService = categoryService;
        this.cjProductStore = cjProductStore;
        this.config = config;
        this.objectMapper = objectMapper;
        this.rawCache = rawCache;
    }

    /**
     * Outcome of a snapshot refresh. {@code upserted} = {@code inserted} + {@code updated}, split so the
     * daily job can report how many products are genuinely NEW vs refreshed. {@code livePids} is the set
     * of pids seen in this fetch — the inverse of {@code removedPids}, fed to
     * {@code CjProductPromotionService.reconcile} so native goods for vanished pids are soft-deleted.
     */
    public record SyncResult(int upserted, int inserted, int updated,
                             List<String> removedPids, java.util.Set<String> livePids) {
        public static SyncResult empty() {
            return new SyncResult(0, 0, 0, List.of(), java.util.Set.of());
        }
    }

    /**
     * Fetch the CJ catalog (paced, via Redis), upsert each product into {@code litemall_cj_product},
     * then soft-delete rows whose pids vanished upstream. Returns counts + the removed raw pids.
     * Disabled CJ indexing is a no-op.
     */
    public SyncResult syncAll() {
        if (!config.isEnabled()) {
            return SyncResult.empty();
        }
        List<CJProduct> products = fetchProducts();

        // Snapshot the currently-live pids BEFORE upserting, so each write classifies as an insert
        // (a genuinely new / resurrected product) vs an update, and the SAME set drives stale
        // detection below without a second DB round-trip.
        java.util.Set<String> preexistingPids = new java.util.HashSet<>(cjProductStore.queryLivePids());

        java.util.Set<String> livePids = new java.util.LinkedHashSet<>();
        int inserted = 0;
        int updated = 0;
        for (CJProduct p : products) {
            if (p == null || p.getPid() == null || p.getPid().isBlank()) {
                continue;
            }
            try {
                cjProductStore.upsert(toRow(p));
                if (preexistingPids.contains(p.getPid())) {
                    updated++;
                } else {
                    inserted++;
                }
                livePids.add(p.getPid());
            } catch (RuntimeException ex) {
                LOGGER.warn("Skipping malformed CJ product pid={}: {}", p.getPid(), ex.getMessage());
            }
        }
        int upserted = inserted + updated;

        // Stale detection: any previously-live row not seen in this fetch is soft-deleted (and its
        // pid returned so the OCS doc cj_<pid> can be dropped). Skipped when the fetch yielded nothing
        // (likely an upstream/auth outage) so a transient failure never wipes the snapshot.
        List<String> removed = new ArrayList<>();
        if (!livePids.isEmpty()) {
            for (String existing : preexistingPids) {
                if (!livePids.contains(existing)) {
                    removed.add(existing);
                }
            }
            if (!removed.isEmpty()) {
                cjProductStore.softDelete(removed);
            }
        }

        // The DB snapshot is now the durable source for everything just fetched, so the consumed raw
        // list staging pages in Redis are dead weight — purge them. Only after a successful land
        // (upserted > 0): a failed/empty fetch keeps the cache so we don't re-hit the 1-req/300s CJ API.
        if (upserted > 0 && config.getRedis().isPurgeAfterSync()) {
            rawCache.purgeRawListKeys();
        }

        LOGGER.info("CJ snapshot sync: {} new, {} updated, {} soft-deleted stale", inserted, updated, removed.size());
        return new SyncResult(upserted, inserted, updated, removed, livePids);
    }

    // ---- CJProduct → snapshot row (normalization lives here) --------------------------------------

    private LitemallCjProduct toRow(CJProduct p) {
        LitemallCjProduct row = new LitemallCjProduct();
        row.setPid(p.getPid()); // RAW CJ pid (UUID) — no cj_ prefix in the DB
        row.setSource(CjProductIndexingService.SOURCE_CJ);
        row.setTitle(title(p));
        row.setImageUrl(p.getProductImage());
        row.setDescription(description(p));
        row.setBrand(null); // CJ has no brand for most items; brand facet stays sparse (acceptable)

        BigDecimal retail = retailPrice(p.getSellPrice());
        row.setPrice(retail);
        row.setDiscountPrice(null);

        CategoryMapping mapping = resolveCategory(p.getCategoryName());
        row.setCategoryNames(writeJson(mapping.names));
        row.setCategoryIds(writeJson(mapping.ids));

        // Default in-stock variant so follow-up #1's multiply stock-scoring doesn't sink CJ docs.
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("stock", config.getDefaultStock());
        if (retail != null) {
            variant.put("variant_price", retail);
        }
        row.setVariantsJson(writeJson(List.of(variant)));
        row.setAttributesJson(null); // CJ carries no curated attributes today
        return row;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            LOGGER.warn("Failed to serialize CJ snapshot field: {}", ex.getMessage());
            return null;
        }
    }

    private static String title(CJProduct p) {
        String en = p.getProductNameEn();
        if (en != null && !en.isBlank()) {
            return en.trim();
        }
        // productName is a JSON array of Chinese names — never use it as a title; fall back to SKU.
        return p.getProductSku() != null ? p.getProductSku() : ("CJ " + p.getPid());
    }

    private static String description(CJProduct p) {
        if (p.getRemark() != null && !p.getRemark().isBlank()) {
            return p.getRemark().trim();
        }
        return title(p);
    }

    /**
     * Retail = wholesale USD × usdToCny × margin, in the local CNY basis; null on unparseable cost.
     * CJ {@code sellPrice} may be a single value ("11.85") OR a variant range ("14.71 -- 64.38") —
     * for a range the lower bound is used (the entry-level "from" price).
     */
    private BigDecimal retailPrice(String sellPrice) {
        if (sellPrice == null || sellPrice.isBlank()) {
            return null;
        }
        // Take the first numeric token, so a "low -- high" range collapses to its lower bound.
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?").matcher(sellPrice);
        if (!m.find()) {
            LOGGER.warn("Unparseable CJ sellPrice '{}'", sellPrice);
            return null;
        }
        CJDropshippingConfig.Pricing pricing = config.getPricing();
        return new BigDecimal(m.group())
                .multiply(pricing.getUsdToCny())
                .multiply(pricing.getMargin())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private record CategoryMapping(List<String> names, List<String> ids) {}

    /**
     * Resolve the CJ {@code categoryName} ("A / B / C") onto the local category tree: map its
     * segments to a local category id (config), then walk that local category root→leaf to produce
     * the SAME {@code category_names}/{@code category_ids} a local product would carry — so CJ folds
     * into the one shared facet. Unmapped → a virtual "Imported" bucket (no junk numeric id).
     */
    private CategoryMapping resolveCategory(String cjCategoryName) {
        List<String> names = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        String[] segments = (cjCategoryName == null) ? new String[0] : cjCategoryName.split("\\s*/\\s*");

        Integer localId = lookupLocalCategory(segments);
        if (localId != null) {
            LitemallCategory category = categoryService.findById(localId);
            while (category != null) {
                names.add(category.getName());
                ids.add(String.valueOf(category.getId()));
                Integer parentId = category.getPid();
                if (parentId == null || parentId.equals(0)) {
                    break;
                }
                category = categoryService.findById(parentId);
            }
            Collections.reverse(names);
            Collections.reverse(ids);
        } else {
            names.add("Imported");
            if (segments.length > 0) {
                names.add(segments[segments.length - 1].trim());
            }
        }
        return new CategoryMapping(names, ids);
    }

    private Integer lookupLocalCategory(String[] segments) {
        List<String> mapping = config.getCategoryMapping();
        if (segments == null || segments.length == 0 || mapping == null || mapping.isEmpty()) {
            return null;
        }
        Map<String, Integer> normalized = new HashMap<>();
        for (String entry : mapping) {
            if (entry == null) {
                continue;
            }
            int eq = entry.lastIndexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) {
                continue;
            }
            String name = entry.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            try {
                normalized.put(name, Integer.valueOf(entry.substring(eq + 1).trim()));
            } catch (NumberFormatException ignore) {
                // skip malformed entry
            }
        }
        for (String segment : segments) {
            if (segment == null) {
                continue;
            }
            Integer id = normalized.get(segment.trim().toLowerCase(Locale.ROOT));
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    // ---- CJ catalog fetch (plan-driven, paced via Redis-through CJProductService) -----------------

    private List<CJProduct> fetchProducts() {
        if (config.getCatalogTargets() != null && !config.getCatalogTargets().isEmpty()) {
            return fetchByPlan();
        }
        try {
            CJProductDataResponse response = cjProductService.fetchProductList();
            if (response != null && response.getData() != null
                    && response.getData().getList() != null && !response.getData().getList().isEmpty()) {
                return response.getData().getList();
            }
            LOGGER.warn("CJ product list empty; falling back to sample (if configured)");
        } catch (RuntimeException ex) {
            LOGGER.warn("CJ product fetch failed ({}); falling back to sample (if configured)", ex.getMessage());
        }
        return loadSample();
    }

    private List<CJProduct> fetchByPlan() {
        List<CJDropshippingConfig.CatalogTarget> targets = config.getCatalogTargets();
        boolean needTree = targets.stream()
                .anyMatch(t -> (t.getCategoryId() == null || t.getCategoryId().isBlank())
                        && t.getCategory() != null && !t.getCategory().isBlank());
        CJCategoryDataResponse tree = null;
        if (needTree) {
            try {
                tree = cjProductService.fetchCategoryList();
            } catch (RuntimeException ex) {
                LOGGER.warn("CJ category tree fetch failed ({}); name-based targets will be skipped", ex.getMessage());
            }
        }
        int pageSize = Math.max(1, config.getPageSize());
        List<CJProduct> all = new ArrayList<>();
        for (CJDropshippingConfig.CatalogTarget target : targets) {
            int limit = target.getLimit() > 0 ? target.getLimit() : 200;
            List<String> categoryIds = resolveCategoryIds(target, tree);
            if (categoryIds.isEmpty()) {
                LOGGER.warn("CJ catalog-target '{}' resolved to no CJ category id; skipping", label(target));
                continue;
            }
            int before = all.size();
            int remaining = limit;
            for (String categoryId : categoryIds) {
                if (remaining <= 0) {
                    break;
                }
                List<CJProduct> got = cjProductService.fetchByCategory(categoryId, remaining, pageSize);
                all.addAll(got);
                remaining -= got.size();
            }
            LOGGER.info("CJ catalog-target '{}' fetched {} products (limit {}, {} leaf categories)",
                    label(target), all.size() - before, limit, categoryIds.size());
        }
        if (all.isEmpty()) {
            LOGGER.warn("CJ plan yielded no products; falling back to sample (if configured)");
            return loadSample();
        }
        return all;
    }

    private static String label(CJDropshippingConfig.CatalogTarget target) {
        return (target.getCategory() != null && !target.getCategory().isBlank())
                ? target.getCategory() : target.getCategoryId();
    }

    private List<String> resolveCategoryIds(CJDropshippingConfig.CatalogTarget target, CJCategoryDataResponse tree) {
        if (target.getCategoryId() != null && !target.getCategoryId().isBlank()) {
            return List.of(target.getCategoryId().trim());
        }
        if (target.getCategory() == null || target.getCategory().isBlank()
                || tree == null || tree.getData() == null) {
            return List.of();
        }
        String want = target.getCategory().trim();
        List<String> leaves = new ArrayList<>();
        for (CJCategoryDataResponse.CategoryData first : tree.getData()) {
            boolean firstMatch = equalsIgnoreCaseTrim(first.getCategoryFirstName(), want);
            if (first.getCategoryFirstList() == null) {
                continue;
            }
            for (CJCategoryDataResponse.CategorySecond second : first.getCategoryFirstList()) {
                boolean secondMatch = equalsIgnoreCaseTrim(second.getCategorySecondName(), want);
                if (second.getCategorySecondList() == null) {
                    continue;
                }
                for (CJCategoryDataResponse.CategoryThird third : second.getCategorySecondList()) {
                    boolean thirdMatch = equalsIgnoreCaseTrim(third.getCategoryName(), want);
                    if ((firstMatch || secondMatch || thirdMatch)
                            && third.getCategoryId() != null && !third.getCategoryId().isBlank()) {
                        leaves.add(third.getCategoryId().trim());
                    }
                }
            }
        }
        return leaves;
    }

    private static boolean equalsIgnoreCaseTrim(String a, String b) {
        return a != null && a.trim().equalsIgnoreCase(b);
    }

    private List<CJProduct> loadSample() {
        String path = config.getSamplePath();
        if (path == null || path.isBlank()) {
            return List.of();
        }
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                LOGGER.warn("CJ sample resource '{}' not found on classpath", path);
                return List.of();
            }
            CJProductDataResponse response = objectMapper.readValue(in, CJProductDataResponse.class);
            if (response != null && response.getData() != null && response.getData().getList() != null) {
                LOGGER.info("Loaded {} CJ products from sample '{}'", response.getData().getList().size(), path);
                return response.getData().getList();
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed to load CJ sample '{}': {}", path, ex.getMessage());
        }
        return List.of();
    }
}
