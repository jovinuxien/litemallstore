package org.linlinjava.litemall.goods.domain.service.elastic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.goods.domain.model.valueobjects.elastic.ProductDocument;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps CJ Dropshipping catalog products into the SAME flat OCS {@link ProductDocument}
 * shape as local goods, so {@code /srv/search} blends both origins into ONE ranked list.
 * The {@code cj_} prefix touches only {@link ProductDocument#getProductId()} +
 * {@link ProductDocument#getSource()} — searchable content (title/price/category) is
 * normalized to be indistinguishable in shape from a local document.
 *
 * <p><b>Storage decision (ADR): index-only.</b> OCS is the CJ catalog store; CJ detail is
 * fetched on demand later. No {@code litemall_goods} rows and no CJ DB table — UUID-keyed
 * CJ data is never shoehorned into the int-keyed local schema (the OCS id is a String, so
 * {@code cj_<pid>} coexists with numeric local ids at zero schema cost). This deliberately
 * supersedes the buggy {@code CjDropshippingApiUtils.convertProduct(...)} /
 * {@code CJProductAdapter} path, which tried to coerce a CJ UUID into a numeric
 * {@code LitemallGoodsId}.
 *
 * <p><b>Per-field normalization (NOT a 1:1 copy):</b>
 * <ul>
 *   <li>{@code title} ← {@code productNameEn} (the English name; {@code productName} is a JSON
 *       array of Chinese names and is not used).</li>
 *   <li>{@code price} ← CJ {@code sellPrice} (wholesale USD) → retail = cost × usdToCny × margin
 *       (config {@code spring.cjdropship.pricing.*}); raw wholesale cost is never indexed.</li>
 *   <li>{@code category_*} ← CJ {@code categoryName} mapped onto the local category tree so CJ
 *       folds into the one shared category facet; unmapped → a virtual "Imported" bucket.</li>
 *   <li>variant {@code stock} ← a default in-stock value so the in-stock scoring boost does not
 *       bury CJ docs (real per-SKU inventory is a follow-up).</li>
 * </ul>
 */
@Service
public class CjProductIndexingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CjProductIndexingService.class);

    /** Marks CJ documents; the order service routes a checkout line to CJ on this prefix / source. */
    public static final String CJ_ID_PREFIX = "cj_";
    public static final String SOURCE_CJ = "cj_dropshipping";

    private final CJProductService cjProductService;
    private final LitemallCategoryService categoryService;
    private final CJDropshippingConfig config;
    private final ObjectMapper objectMapper;

    public CjProductIndexingService(CJProductService cjProductService,
                                    LitemallCategoryService categoryService,
                                    CJDropshippingConfig config,
                                    ObjectMapper objectMapper) {
        this.cjProductService = cjProductService;
        this.categoryService = categoryService;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * Build CJ documents from the current product page (cached/rate-limited by
     * {@link CJProductService}); empty when CJ indexing is disabled. Falls back to a committed
     * sample response when the live CJ API is unreachable, so the index→search path stays provable.
     */
    public List<ProductDocument> buildDocuments() {
        if (!config.isEnabled()) {
            return List.of();
        }
        List<CJProduct> products = fetchProducts();
        List<ProductDocument> docs = new ArrayList<>(products.size());
        for (CJProduct p : products) {
            if (p == null || p.getPid() == null || p.getPid().isBlank()) {
                continue;
            }
            try {
                docs.add(toDocument(p));
            } catch (RuntimeException ex) {
                LOGGER.warn("Skipping malformed CJ product pid={}: {}", p.getPid(), ex.getMessage());
            }
        }
        LOGGER.info("Built {} CJ product documents for OCS", docs.size());
        return docs;
    }

    /** Map a single CJ product to the flat OCS document shape (id {@code cj_<pid>}, source CJ). */
    public ProductDocument toDocument(CJProduct p) {
        ProductDocument doc = new ProductDocument();
        doc.setProductId(CJ_ID_PREFIX + p.getPid());
        doc.setSource(SOURCE_CJ);
        doc.setTitle(title(p));
        doc.setImageUrl(p.getProductImage());
        doc.setDescription(description(p));
        doc.setBrand(null); // CJ has no brand for most items; brand facet stays sparse (acceptable)

        BigDecimal retail = retailPrice(p.getSellPrice());
        doc.setPrice(retail);
        doc.setDiscountPrice(null);

        applyCategory(doc, p.getCategoryName());

        // Default in-stock variant so follow-up #1's multiply stock-scoring doesn't sink CJ docs.
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("stock", config.getDefaultStock());
        if (retail != null) {
            variant.put("variant_price", retail);
        }
        doc.addVariant(variant);
        return doc;
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

    /**
     * Resolve the CJ {@code categoryName} ("A / B / C") onto the local category tree: map its
     * top-level segment to a local category id (config), then walk that local category root→leaf to
     * produce the SAME {@code category_names}/{@code category_ids} a local product would carry — so
     * CJ folds into the one shared facet. Unmapped → a virtual "Imported" bucket (no junk numeric id).
     */
    private void applyCategory(ProductDocument doc, String cjCategoryName) {
        List<String> names = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        // CJ categoryName may be a full path ("A / B / C") or a single leaf ("Suits & Sets").
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
            // Virtual fallback bucket: readable name path, NO numeric id (never a junk/DB-colliding id).
            names.add("Imported");
            if (segments.length > 0) {
                names.add(segments[segments.length - 1].trim());
            }
        }
        doc.setCategoryNames(names);
        doc.setCategoryIds(ids);
    }

    /**
     * Case-insensitive lookup of a local category id for a CJ category path. Tries each segment
     * (top→leaf) so it matches whether CJ supplies a full "A / B / C" path or just a leaf name.
     */
    private Integer lookupLocalCategory(String[] segments) {
        List<String> mapping = config.getCategoryMapping();
        if (segments == null || segments.length == 0 || mapping == null || mapping.isEmpty()) {
            return null;
        }
        // Parse "Category Name=<localId>" entries into a normalized name→id map (split on the LAST '=').
        Map<String, Integer> normalized = new java.util.HashMap<>();
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

    private List<CJProduct> fetchProducts() {
        // Plan-driven path: fetch the configured categories up to each limit (paced, rate-limited).
        if (config.getCatalogTargets() != null && !config.getCatalogTargets().isEmpty()) {
            return fetchByPlan();
        }
        // Legacy single-page path (no plan configured) + sample fallback.
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

    /**
     * Execute the {@code catalog-targets} fetch plan: per target, resolve its CJ category name to
     * leaf category ids (or use an explicit {@code categoryId}), then pull products via
     * {@link CJProductService#fetchByCategory} across those leaves until the target's {@code limit}
     * is reached. Each upstream call is paced by the configured CJ quota. Falls back to the committed
     * sample only when the whole plan yields nothing (so the index→search path stays provable offline).
     */
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

    /**
     * Resolve a target to CJ leaf category ids: an explicit {@code categoryId} wins; otherwise match
     * the {@code category} name case-insensitively against ANY level of the CJ category tree and
     * collect the leaf (3rd-level) ids under the match — so a first-level name like
     * "Toys, Kids & Babies" expands to every leaf beneath it (the fetch then stops at the limit, so
     * it never fans out across all leaves needlessly).
     */
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
