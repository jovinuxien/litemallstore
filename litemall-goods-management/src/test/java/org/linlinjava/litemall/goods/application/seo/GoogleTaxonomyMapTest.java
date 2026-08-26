package org.linlinjava.litemall.goods.application.seo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.goods.application.pricing.CategoryMarginResolver;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * The feed's {@code google_product_category} resolution.
 *
 * <p>The behaviour under test is the 2026-08-26 change: resolve the SUBCATEGORY first, and only
 * fall back to the L1 root. Before it, all 4,055 live products declared one of two values, which
 * is close to no signal for a system that matches shopper queries against this column.
 */
public class GoogleTaxonomyMapTest {

    private LitemallCategoryService categoryService;
    private CategoryMarginResolver marginResolver;
    private GoogleTaxonomyMap map;

    @BeforeEach
    void setUp() {
        categoryService = Mockito.mock(LitemallCategoryService.class);
        marginResolver = Mockito.mock(CategoryMarginResolver.class);
        map = new GoogleTaxonomyMap(marginResolver, categoryService);
    }

    /** id, name, pid — a root is modelled as pid == id, which is how the seeded tree marks one. */
    private LitemallCategory category(int id, String name, int pid) {
        LitemallCategory c = new LitemallCategory();
        c.setId(id);
        c.setName(name);
        c.setPid(pid);
        c.setDeleted(false);
        when(categoryService.findById(id)).thenReturn(c);
        return c;
    }

    @Test
    void resolvesTheSubcategoryRatherThanTheRoot() {
        category(1036495, "Home Improvement", 1036495);
        category(1036504, "Tools", 1036495);
        when(marginResolver.rootOfCategory(anyInt())).thenReturn(1036495);

        assertThat(map.resolve(1036504)).isEqualTo("Hardware > Tools");
    }

    @Test
    void aLeafInheritsItsSubcategoryMapping() {
        // The goods' own category is usually a CJ leaf that is not in the table; the walk must
        // keep going up until something matches.
        category(1036495, "Home Improvement", 1036495);
        category(1036504, "Tools", 1036495);
        category(9001, "Cordless Drill Bits", 1036504);
        when(marginResolver.rootOfCategory(anyInt())).thenReturn(1036495);

        assertThat(map.resolve(9001)).isEqualTo("Hardware > Tools");
    }

    @Test
    void fallsBackToTheL1TableWhenNoSubcategoryMatches() {
        category(1036143, "Home, Garden & Furniture", 1036143);
        category(9002, "Unmapped Aisle", 1036143);
        when(marginResolver.rootOfCategory(9002)).thenReturn(1036143);

        assertThat(map.resolve(9002)).isEqualTo("Home & Garden");
    }

    @Test
    void theThreeLightingSubcategoriesShareTheOnePathThatActuallyExists() {
        // Google has no "Lighting > Outdoor Lighting" node, and "Lawn & Garden > Outdoor Living"
        // would claim these are patio furniture. A shallower TRUE path beats a deeper invented one.
        category(1036495, "Home Improvement", 1036495);
        category(1036496, "LED Lighting", 1036495);
        category(1036498, "Outdoor Lighting", 1036495);
        category(1036522, "Indoor Lighting", 1036495);
        when(marginResolver.rootOfCategory(anyInt())).thenReturn(1036495);

        assertThat(map.resolve(1036496)).isEqualTo("Home & Garden > Lighting");
        assertThat(map.resolve(1036498)).isEqualTo("Home & Garden > Lighting");
        assertThat(map.resolve(1036522)).isEqualTo("Home & Garden > Lighting");
    }

    @Test
    void everyLiveSubcategoryResolvesDeeperThanItsRoot() {
        category(1036143, "Home, Garden & Furniture", 1036143);
        when(marginResolver.rootOfCategory(anyInt())).thenReturn(1036143);
        String[][] expected = {
                {"Home Textiles", "Home & Garden > Linens & Bedding"},
                {"Kitchen, Dining & Bar", "Home & Garden > Kitchen & Dining"},
                {"Home Storage", "Home & Garden > Household Supplies > Storage & Organization"},
                {"Festive & Party Supplies",
                        "Arts & Entertainment > Party & Celebration > Party Supplies"},
                {"Arts, Crafts & Sewing",
                        "Arts & Entertainment > Hobbies & Creative Arts > Arts & Crafts"},
                {"Musical Instruments",
                        "Arts & Entertainment > Hobbies & Creative Arts > Musical Instruments"},
                {"Home Appliances", "Home & Garden > Household Appliances"},
        };
        int id = 7000;
        for (String[] pair : expected) {
            int cid = id++;
            category(cid, pair[0], 1036143);
            assertThat(map.resolve(cid)).as(pair[0]).isEqualTo(pair[1]);
        }
    }

    @Test
    void nameMatchingIsCaseAndWhitespaceInsensitive() {
        category(1036495, "Home Improvement", 1036495);
        category(1036504, "  TOOLS  ", 1036495);
        when(marginResolver.rootOfCategory(anyInt())).thenReturn(1036495);

        assertThat(map.resolve(1036504)).isEqualTo("Hardware > Tools");
    }

    @Test
    void unmappedAndUnresolvableCategoriesYieldBlankRatherThanAGuess() {
        assertThat(map.resolve(null)).isEmpty();
        assertThat(map.resolve(0)).isEmpty();

        category(8000, "Nowhere", 8000);
        when(marginResolver.rootOfCategory(8000)).thenReturn(null);
        assertThat(map.resolve(8000)).isEmpty();
    }

    @Test
    void aResolutionFailureNeverBreaksTheFeedBuild() {
        category(8100, "Boom", 8100);
        when(marginResolver.rootOfCategory(8100)).thenThrow(new IllegalStateException("tree down"));

        assertThat(map.resolve(8100)).isEmpty();
    }

    @Test
    void aCyclicParentChainTerminates() {
        // Two categories pointing at each other must not spin the walk.
        LitemallCategory a = new LitemallCategory();
        a.setId(8200);
        a.setName("A");
        a.setPid(8201);
        a.setDeleted(false);
        LitemallCategory b = new LitemallCategory();
        b.setId(8201);
        b.setName("B");
        b.setPid(8200);
        b.setDeleted(false);
        when(categoryService.findById(8200)).thenReturn(a);
        when(categoryService.findById(8201)).thenReturn(b);
        when(marginResolver.rootOfCategory(8200)).thenReturn(null);

        assertThat(map.resolve(8200)).isEmpty();
    }

    @Test
    void theSecondLookupIsServedFromCache() {
        category(1036495, "Home Improvement", 1036495);
        category(1036504, "Tools", 1036495);
        when(marginResolver.rootOfCategory(anyInt())).thenReturn(1036495);

        map.resolve(1036504);
        map.resolve(1036504);
        map.resolve(1036504);

        // One walk, not three: the feed calls this once per row across ~11 distinct subcategories.
        Mockito.verify(categoryService, Mockito.times(1)).findById(1036504);
    }
}
