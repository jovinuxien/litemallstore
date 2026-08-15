package org.linlinjava.litemall.goods.infrastructure.configuration;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 26 Phase 2: the rule that keeps a narrowed storefront narrowed.
 *
 * <p>Narrowing off-sales the goods that exist on the day it runs, but the CJ pipeline keeps
 * mirroring all 14 L1s and the nightly promote lands every NEW product on sale. Measured on prod
 * the morning after narrowing: on-sale had climbed 2,200 → 2,668, none of it narrowed goods
 * returning — 428 brand-new goods, 328 of them outside the anchor. Left alone, the storefront
 * drifts back to broad within weeks and the narrowing silently undoes itself.
 */
public class AnchorNarrowingPropertiesTest {

    private static final int ANCHOR_A = 1036143;
    private static final int ANCHOR_B = 1036495;
    private static final int OTHER = 1036012;

    private LitemallGoodsProperties narrowedTo(Integer... roots) {
        LitemallGoodsProperties p = new LitemallGoodsProperties();
        p.setAnchorCategoryIds(List.of(roots));
        return p;
    }

    @Test
    public void unconfiguredMeansOffAndNothingChanges() {
        LitemallGoodsProperties p = new LitemallGoodsProperties();
        assertFalse(p.isOutsideAnchor(OTHER), "no anchor configured must be byte-identical to before");
        assertFalse(p.isOutsideAnchor(ANCHOR_A));
        assertFalse(p.isOutsideAnchor(null));

        p.setAnchorCategoryIds(null);
        assertFalse(p.isOutsideAnchor(OTHER), "a null list must not start off-saling the catalogue");
    }

    @Test
    public void anchorRootsAreInsideAndEverythingElseIsOutside() {
        LitemallGoodsProperties p = narrowedTo(ANCHOR_A, ANCHOR_B);
        assertFalse(p.isOutsideAnchor(ANCHOR_A));
        assertFalse(p.isOutsideAnchor(ANCHOR_B));
        assertTrue(p.isOutsideAnchor(OTHER));
    }

    /**
     * An unresolvable root counts as outside — "we cannot place it" must not silently mean "put it
     * in the storefront". Matches the call the narrowing sweep makes for orphaned categories.
     */
    @Test
    public void anUnresolvableRootIsTreatedAsOutside() {
        assertTrue(narrowedTo(ANCHOR_A).isOutsideAnchor(null));
    }
}
