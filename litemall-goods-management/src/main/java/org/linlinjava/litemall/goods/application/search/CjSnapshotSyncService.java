package org.linlinjava.litemall.goods.application.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Refreshes the {@code litemall_cj_product} snapshot — the durable, DB-backed source the OCS index
 * is built from — from the CJ Dropshipping API, via the Redis staging buffer.
 *
 * <p>Pipeline: fetch the configured {@code catalog-targets} through {@link CJProductService} (which
 * reads-through Redis and paces live calls by the CJ quota) → NORMALIZE each {@link CJProduct} into a
 * customer-ready {@link LitemallCjProduct} row (English title, retail-marked-up price, RAW CJ category
 * path + leaf UUID — resolved onto the local tree at promotion time, default-stock variant) → upsert.
 * The row's primary key is the RAW CJ pid
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
    private final LitemallCjProductService cjProductStore;
    private final CJDropshippingConfig config;
    private final ObjectMapper objectMapper;
    private final CjRawCacheRepository rawCache;
    private final CjCategoryTreeSyncService categoryTreeSync;
    private final CjPricing pricing;

    public CjSnapshotSyncService(CJProductService cjProductService,
                                 LitemallCjProductService cjProductStore,
                                 CJDropshippingConfig config,
                                 ObjectMapper objectMapper,
                                 CjRawCacheRepository rawCache,
                                 CjCategoryTreeSyncService categoryTreeSync,
                                 CjPricing pricing) {
        this.cjProductService = cjProductService;
        this.cjProductStore = cjProductStore;
        this.config = config;
        this.objectMapper = objectMapper;
        this.rawCache = rawCache;
        this.categoryTreeSync = categoryTreeSync;
        this.pricing = pricing;
    }

    /**
     * Outcome of a snapshot refresh. {@code upserted} = {@code inserted} + {@code updated}, split so the
     * daily job can report how many products are genuinely NEW vs refreshed. {@code livePids} is the set
     * of pids seen in this fetch — the inverse of {@code removedPids}, fed to
     * {@code CjProductPromotionService.reconcile} so native goods for vanished pids are soft-deleted.
     * {@code insertedPids} (Wave 12) lists the genuinely NEW/resurrected pids so the inventory flow can
     * route them as NEW_ARRIVAL (deal-candidate scoring) without re-deriving the classification.
     */
    public record SyncResult(int upserted, int inserted, int updated,
                             List<String> removedPids, java.util.Set<String> livePids,
                             List<String> insertedPids,
                             boolean complete) {
        public static SyncResult empty() {
            return new SyncResult(0, 0, 0, List.of(), java.util.Set.of(), List.of(), false);
        }
    }

    /**
     * Fetch the CJ catalog (paced, via Redis), upsert each product into {@code litemall_cj_product},
     * then soft-delete rows whose pids vanished upstream. Returns counts + the removed raw pids.
     * Disabled CJ indexing is a no-op.
     */
    public SyncResult syncAll() {
        return syncAll(null);
    }

    /**
     * Same as {@link #syncAll()} but fetches a caller-supplied set of targets (each a CJ category +
     * a per-category product limit) instead of the configured {@code catalogTargets}. A null/empty
     * override falls back to the configured plan, so the nightly job and an ad-hoc admin run share
     * one code path. Everything downstream (Redis pacing, snapshot upsert, stale detection) is identical.
     */
    public SyncResult syncAll(List<CJDropshippingConfig.CatalogTarget> targetsOverride) {
        if (!config.isEnabled()) {
            return SyncResult.empty();
        }

        // Mirror the CJ category tree first (cheap: the tree call is Redis-cached), so the
        // promotion pass that follows a sync can resolve every product's leaf UUID onto a real
        // root→leaf local chain. A failed fetch degrades to a no-op inside syncTree.
        try {
            categoryTreeSync.syncTree();
        } catch (RuntimeException ex) {
            LOGGER.warn("CJ category tree sync failed ({}); promotion will use the persisted mirror",
                    ex.getMessage());
        }

        FetchOutcome fetch = fetchProducts(targetsOverride);
        List<CJProduct> products = fetch.products();

        // A targeted run (explicit targetsOverride) covers only the chosen categories, so it is
        // ADDITIVE — it must NOT treat the rest of the catalog as "vanished". Stale detection /
        // soft-delete only runs for a FULL sync (the configured plan, override null/empty) whose
        // fetch plan COMPLETED: a partial fetch (category tree down, a target skipped or yielding
        // nothing, sample fallback) sees only a slice of the catalog, and pruning against a slice
        // is what eroded 7.7k of 7.8k products across 07-05..07-10.
        boolean pruneStale = (targetsOverride == null || targetsOverride.isEmpty()) && fetch.complete();
        if (!fetch.complete() && (targetsOverride == null || targetsOverride.isEmpty())) {
            LOGGER.warn("CJ fetch plan incomplete — stale-pruning skipped for this run (additive upsert only)");
        }

        // Snapshot the currently-live pids BEFORE upserting, so each write classifies as an insert
        // (a genuinely new / resurrected product) vs an update, and the SAME set drives stale
        // detection below without a second DB round-trip.
        java.util.Set<String> preexistingPids = new java.util.HashSet<>(cjProductStore.queryLivePids());

        java.util.Set<String> livePids = new java.util.LinkedHashSet<>();
        List<String> insertedPids = new ArrayList<>();
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
                    insertedPids.add(p.getPid());
                }
                livePids.add(p.getPid());
            } catch (RuntimeException ex) {
                LOGGER.warn("Skipping malformed CJ product pid={}: {}", p.getPid(), ex.getMessage());
            }
        }
        int inserted = insertedPids.size();
        int upserted = inserted + updated;

        // Stale detection: any previously-live row not seen in this fetch is soft-deleted (and its
        // pid returned so the OCS doc cj_<pid> can be dropped). Skipped when the fetch yielded nothing
        // (likely an upstream/auth outage) so a transient failure never wipes the snapshot — and
        // skipped entirely for a targeted run, which only knows about its chosen categories.
        List<String> removed = new ArrayList<>();
        if (pruneStale && !livePids.isEmpty()) {
            for (String existing : preexistingPids) {
                if (!livePids.contains(existing)) {
                    removed.add(existing);
                }
            }
            // Erosion tripwire: CJ listings rotate, so a large "vanished" set is far more likely a
            // partial/rotated fetch than genuine mass delisting. Refuse to prune above the configured
            // fraction; an intentional rebuild raises cj.prune-max-fraction deliberately.
            if (!removed.isEmpty() && !preexistingPids.isEmpty()) {
                double fraction = (double) removed.size() / preexistingPids.size();
                if (fraction > config.getPruneMaxFraction()) {
                    LOGGER.error("CJ stale-prune tripwire: refusing to soft-delete {} of {} live products "
                                    + "({}% > {}%) — partial fetch or listing rotation suspected; prune skipped",
                            removed.size(), preexistingPids.size(),
                            Math.round(fraction * 100), Math.round(config.getPruneMaxFraction() * 100));
                    removed.clear();
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
        return new SyncResult(upserted, inserted, updated, removed, livePids, insertedPids, fetch.complete());
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

        // Wave 12: persist the raw USD cost (range lower bound) alongside the marked-up retail.
        BigDecimal cost = pricing.parseCost(p.getSellPrice());
        BigDecimal retail = pricing.retail(cost);
        row.setSellPrice(cost);
        row.setPrice(retail);
        row.setDiscountPrice(null);

        CategoryMapping mapping = rawCategory(p);
        row.setCategoryNames(writeJson(mapping.names));
        row.setCategoryIds(writeJson(mapping.ids));

        // Default in-stock variant so follow-up #1's multiply stock-scoring doesn't sink CJ docs.
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("stock", config.getDefaultStock());
        if (retail != null) {
            variant.put("variant_price", retail);
        }
        if (cost != null) {
            variant.put("variant_sell_price", cost);
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

    private record CategoryMapping(List<String> names, List<String> ids) {}

    /**
     * Store the CJ category RAW: {@code category_names} = the "A / B / C" path segments (leaf
     * last), {@code category_ids} = the leaf's CJ category UUID. The snapshot no longer collapses
     * the path onto the static config map — resolution onto the local tree happens at promotion
     * time ({@code CjProductPromotionService}), against the full CJ tree that
     * {@link CjCategoryTreeSyncService} mirrors into {@code litemall_category}.
     */
    private CategoryMapping rawCategory(CJProduct p) {
        List<String> names = new ArrayList<>();
        if (p.getCategoryName() != null) {
            for (String segment : p.getCategoryName().split("\\s*/\\s*")) {
                if (segment != null && !segment.isBlank()) {
                    names.add(segment.trim());
                }
            }
        }
        List<String> ids = (p.getCategoryId() != null && !p.getCategoryId().isBlank())
                ? List.of(p.getCategoryId().trim())
                : List.of();
        return new CategoryMapping(names, ids);
    }

    // ---- CJ catalog fetch (plan-driven, paced via Redis-through CJProductService) -----------------

    /**
     * Fetch haul + whether the plan COMPLETED. {@code complete} is false when any part of the plan
     * was skipped or yielded nothing (tree fetch failure, unresolvable target, zero-haul target,
     * sample fallback) — a signal that this haul must not drive stale-pruning.
     */
    private record FetchOutcome(List<CJProduct> products, boolean complete) {}

    private FetchOutcome fetchProducts(List<CJDropshippingConfig.CatalogTarget> targetsOverride) {
        List<CJDropshippingConfig.CatalogTarget> targets =
                (targetsOverride != null && !targetsOverride.isEmpty()) ? targetsOverride : config.getCatalogTargets();
        if (targets != null && !targets.isEmpty()) {
            return fetchByPlan(targets);
        }
        try {
            CJProductDataResponse response = cjProductService.fetchProductList();
            if (response != null && response.getData() != null
                    && response.getData().getList() != null && !response.getData().getList().isEmpty()) {
                return new FetchOutcome(response.getData().getList(), true);
            }
            LOGGER.warn("CJ product list empty; falling back to sample (if configured)");
        } catch (RuntimeException ex) {
            LOGGER.warn("CJ product fetch failed ({}); falling back to sample (if configured)", ex.getMessage());
        }
        return new FetchOutcome(loadSample(), false);
    }

    private FetchOutcome fetchByPlan(List<CJDropshippingConfig.CatalogTarget> targets) {
        boolean complete = true;
        boolean needTree = targets.stream()
                .anyMatch(t -> (t.getCategoryId() == null || t.getCategoryId().isBlank())
                        && t.getCategory() != null && !t.getCategory().isBlank());
        CJCategoryDataResponse tree = null;
        if (needTree) {
            try {
                tree = cjProductService.fetchCategoryList();
            } catch (RuntimeException ex) {
                LOGGER.warn("CJ category tree fetch failed ({}); name-based targets will be skipped", ex.getMessage());
                complete = false;
            }
        }
        int pageSize = Math.max(1, config.getPageSize());
        List<CJProduct> all = new ArrayList<>();
        for (CJDropshippingConfig.CatalogTarget target : targets) {
            List<String> categoryIds = resolveCategoryIds(target, tree);
            if (categoryIds.isEmpty()) {
                LOGGER.warn("CJ catalog-target '{}' resolved to no CJ category id; skipping", label(target));
                complete = false;
                continue;
            }
            int before = all.size();
            int perLeaf = target.getPerLeafLimit();
            if (perLeaf > 0) {
                // Per-leaf budgets: every resolved leaf gets its own quota, so a broad target fills
                // ALL its leaves; `limit` (when > 0) only caps the target's overall haul.
                int cap = target.getLimit() > 0 ? target.getLimit() : Integer.MAX_VALUE;
                for (String categoryId : categoryIds) {
                    int taken = all.size() - before;
                    if (taken >= cap) {
                        break;
                    }
                    List<CJProduct> got = cjProductService.fetchByCategory(
                            categoryId, Math.min(perLeaf, cap - taken), pageSize);
                    all.addAll(got);
                }
                if (all.size() - before == 0) {
                    complete = false;
                }
                LOGGER.info("CJ catalog-target '{}' fetched {} products (per-leaf {}, {} leaf categories)",
                        label(target), all.size() - before, perLeaf, categoryIds.size());
            } else {
                int limit = target.getLimit() > 0 ? target.getLimit() : 200;
                int remaining = limit;
                for (String categoryId : categoryIds) {
                    if (remaining <= 0) {
                        break;
                    }
                    List<CJProduct> got = cjProductService.fetchByCategory(categoryId, remaining, pageSize);
                    all.addAll(got);
                    remaining -= got.size();
                }
                if (all.size() - before == 0) {
                    complete = false;
                }
                LOGGER.info("CJ catalog-target '{}' fetched {} products (limit {}, {} leaf categories)",
                        label(target), all.size() - before, limit, categoryIds.size());
            }
        }
        if (all.isEmpty()) {
            LOGGER.warn("CJ plan yielded no products; falling back to sample (if configured)");
            return new FetchOutcome(loadSample(), false);
        }
        return new FetchOutcome(all, complete);
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
