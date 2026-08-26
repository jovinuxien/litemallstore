package org.linlinjava.litemall.goods.application.seo;

import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.goods.application.pricing.CategoryMarginResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wave 25: static CJ category → Google product taxonomy mapping for the merchant feed's
 * {@code google_product_category} column. Values are full-path strings from Google's published
 * taxonomy (accepted verbatim by both Google Merchant Center and Meta Commerce Manager).
 *
 * <p>Unmapped categories yield an empty string — the column stays honest rather than guessing.
 *
 * <h2>Why this resolves the SUBCATEGORY first (2026-08-26)</h2>
 * The original version resolved every goods up to its L1 root, so after the Wave-26 narrowing
 * all 4,055 live products filed under exactly two values: "Home &amp; Garden" (2,736) and
 * "Hardware" (1,319). Google matches shopper queries against this column, and two buckets for a
 * whole catalogue is close to no signal at all — a table lamp and a hedge trimmer were declared
 * to be the same kind of thing. The walk below therefore checks each ancestor from the most
 * specific upwards and takes the first mapped name, falling back to the L1 table.
 *
 * <p><b>Every path here was validated against Google's published taxonomy file</b>
 * (taxonomy-with-ids.en-US, version 2021-09-21) rather than written from memory: Google rejects
 * unknown category strings, and three plausible-looking guesses — "Home &amp; Garden &gt;
 * Lighting &gt; Outdoor Lighting", "Hardware &gt; Lighting Fixtures", "Home &amp; Garden &gt;
 * Decor &gt; Storage &amp; Organization" — do not exist. When adding a mapping, check the file;
 * a shallower TRUE path beats a deeper invented one.
 */
@Component
public class GoogleTaxonomyMap {

    /**
     * Keyed by normalized subcategory name. Matched against every ancestor of the goods'
     * category, most specific first, so a leaf under "Tools" resolves to "Hardware &gt; Tools"
     * even when the leaf itself is unmapped.
     *
     * <p>The three lighting subcategories deliberately share "Home &amp; Garden &gt; Lighting":
     * Google has no "Outdoor Lighting" node under Lighting (only Flood &amp; Spot Lights,
     * In-Ground Lights and Landscape Pathway Lighting, which are narrower than what these
     * subcategories actually hold), and filing outdoor lamps under "Lawn &amp; Garden &gt;
     * Outdoor Living" — which does exist — would claim they are patio furniture.
     */
    private static final Map<String, String> BY_SUBCATEGORY_NAME = Map.ofEntries(
            // Home, Garden & Furniture
            Map.entry("home textiles", "Home & Garden > Linens & Bedding"),
            Map.entry("kitchen, dining & bar", "Home & Garden > Kitchen & Dining"),
            Map.entry("home storage", "Home & Garden > Household Supplies > Storage & Organization"),
            Map.entry("festive & party supplies",
                    "Arts & Entertainment > Party & Celebration > Party Supplies"),
            Map.entry("arts, crafts & sewing",
                    "Arts & Entertainment > Hobbies & Creative Arts > Arts & Crafts"),
            Map.entry("musical instruments",
                    "Arts & Entertainment > Hobbies & Creative Arts > Musical Instruments"),
            // Home Improvement
            Map.entry("tools", "Hardware > Tools"),
            Map.entry("home appliances", "Home & Garden > Household Appliances"),
            Map.entry("led lighting", "Home & Garden > Lighting"),
            Map.entry("indoor lighting", "Home & Garden > Lighting"),
            Map.entry("outdoor lighting", "Home & Garden > Lighting"));

    /** Keyed by normalized L1 root name — the 14 live CJ roots. The fallback, not the first try. */
    private static final Map<String, String> BY_L1_NAME = Map.ofEntries(
            Map.entry("women's clothing", "Apparel & Accessories > Clothing"),
            Map.entry("men's clothing", "Apparel & Accessories > Clothing"),
            Map.entry("toys, kids & babies", "Toys & Games"),
            Map.entry("consumer electronics", "Electronics"),
            Map.entry("pet supplies", "Animals & Pet Supplies > Pet Supplies"),
            Map.entry("home, garden & furniture", "Home & Garden"),
            Map.entry("health, beauty & hair", "Health & Beauty"),
            Map.entry("sports & outdoors", "Sporting Goods"),
            Map.entry("automobiles & motorcycles", "Vehicles & Parts > Vehicle Parts & Accessories"),
            Map.entry("jewelry & watches", "Apparel & Accessories > Jewelry"),
            Map.entry("phones & accessories", "Electronics > Communications > Telephony > Mobile Phone Accessories"),
            Map.entry("computer & office", "Electronics > Computers"),
            Map.entry("bags & shoes", "Apparel & Accessories"),
            Map.entry("home improvement", "Hardware"));

    /** Depth bound on the parent walk — a self-referencing or cyclic pid must not spin. */
    private static final int MAX_DEPTH = 8;

    private final CategoryMarginResolver categoryResolver;
    private final LitemallCategoryService categoryService;

    /**
     * categoryId → resolved path. The feed asks once per row (4,055 rows over ~11 distinct
     * subcategories), and without this each row would walk the tree with a findById per level.
     * Never invalidated: the category tree changes only when the CJ sync lands a new leaf, and
     * the next nightly rebuild runs in a process that starts this cache empty.
     */
    private final Map<Integer, String> cache = new ConcurrentHashMap<>();

    public GoogleTaxonomyMap(CategoryMarginResolver categoryResolver,
                             LitemallCategoryService categoryService) {
        this.categoryResolver = categoryResolver;
        this.categoryService = categoryService;
    }

    /** Google taxonomy path for a goods' category (any level), or "" when unmapped/unresolvable. */
    public String resolve(Integer categoryId) {
        if (categoryId == null || categoryId <= 0) {
            return "";
        }
        return cache.computeIfAbsent(categoryId, this::resolveUncached);
    }

    private String resolveUncached(Integer categoryId) {
        try {
            // Most specific first: the goods' own category, then each ancestor.
            for (String name : ancestorNames(categoryId)) {
                String mapped = BY_SUBCATEGORY_NAME.get(name);
                if (mapped != null) {
                    return mapped;
                }
            }
            // Nothing matched on the way up — fall back to the L1 table, which is what this
            // class did for every row before subcategory resolution existed.
            Integer rootId = categoryResolver.rootOfCategory(categoryId);
            if (rootId == null) {
                return "";
            }
            LitemallCategory root = categoryService.findById(rootId);
            if (root == null || root.getName() == null) {
                return "";
            }
            return BY_L1_NAME.getOrDefault(normalize(root.getName()), "");
        } catch (RuntimeException ex) {
            return ""; // feed decoration only — never fail a build over taxonomy resolution
        }
    }

    /** Normalized names from the category itself up to its root, nearest first. */
    private List<String> ancestorNames(Integer categoryId) {
        List<String> names = new ArrayList<>(MAX_DEPTH);
        Integer id = categoryId;
        for (int depth = 0; depth < MAX_DEPTH && id != null && id > 0; depth++) {
            LitemallCategory category = categoryService.findById(id);
            if (category == null || Boolean.TRUE.equals(category.getDeleted())) {
                break;
            }
            if (category.getName() != null) {
                names.add(normalize(category.getName()));
            }
            Integer pid = category.getPid();
            // A row whose pid points at itself is how the seeded tree marks a top level; without
            // this the walk would loop until MAX_DEPTH on every single call.
            if (pid == null || pid.equals(id)) {
                break;
            }
            id = pid;
        }
        return names;
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
