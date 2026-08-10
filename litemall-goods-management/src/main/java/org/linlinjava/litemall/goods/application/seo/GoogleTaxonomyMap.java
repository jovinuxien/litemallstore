package org.linlinjava.litemall.goods.application.seo;

import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.goods.application.pricing.CategoryMarginResolver;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Wave 25: static CJ-L1 → Google product taxonomy mapping for the merchant feed's
 * {@code google_product_category} column. Any leaf/level category id resolves up to its L1 root
 * (the same cached walk the margin overrides use), whose NAME keys this table. Values are
 * full-path strings from Google's published taxonomy (accepted verbatim by both Google Merchant
 * Center and Meta Commerce Manager).
 *
 * <p>Unmapped roots (legacy yanxuan L1s, the "Imported" fallback root, future CJ L1s not yet
 * added here) yield an empty string — the column stays honest rather than guessing.
 */
@Component
public class GoogleTaxonomyMap {

    /** Keyed by normalized (lower-cased, trimmed) L1 root name — the 14 live CJ roots. */
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

    private final CategoryMarginResolver categoryResolver;
    private final LitemallCategoryService categoryService;

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
        try {
            Integer rootId = categoryResolver.rootOfCategory(categoryId);
            if (rootId == null) {
                return "";
            }
            LitemallCategory root = categoryService.findById(rootId);
            if (root == null || root.getName() == null) {
                return "";
            }
            return BY_L1_NAME.getOrDefault(root.getName().trim().toLowerCase(Locale.ROOT), "");
        } catch (RuntimeException ex) {
            return ""; // feed decoration only — never fail a build over taxonomy resolution
        }
    }
}
