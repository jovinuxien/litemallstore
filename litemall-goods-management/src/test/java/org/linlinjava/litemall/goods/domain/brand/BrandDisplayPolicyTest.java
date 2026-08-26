package org.linlinjava.litemall.goods.domain.brand;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallBrand;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gate itself, independent of any caller — so the four sites that now share it are pinned
 * against one table of cases rather than four inline re-readings of the same rule.
 */
public class BrandDisplayPolicyTest {

    private static LitemallBrand brand(Integer kind, Boolean displayEnabled, Boolean deleted) {
        LitemallBrand brand = new LitemallBrand();
        brand.setName("Yiwu Ruijia Auto Supplies Co., Ltd.");
        brand.setKind(kind == null ? null : kind.byteValue());
        brand.setDisplayEnabled(displayEnabled);
        brand.setDeleted(deleted);
        return brand;
    }

    @Test
    public void curatedConsumerBrandIsBothDisplayableAndABrand() {
        LitemallBrand brand = brand(0, true, false);
        assertTrue(BrandDisplayPolicy.isDisplayable(brand));
        assertTrue(BrandDisplayPolicy.isConsumerBrand(brand));
    }

    /**
     * The distinction the two predicates exist for: an enabled supplier store MAY be shown where
     * the UI labels it ("Sold by" on the PDP), and MUST NOT be shown where the value stands as a
     * bare brand claim (the merchant feed's brand column, the search facet headed "Brand").
     */
    @Test
    public void enabledSupplierStoreIsDisplayableButNotABrand() {
        LitemallBrand store = brand(1, true, false);
        assertTrue(BrandDisplayPolicy.isDisplayable(store));
        assertFalse(BrandDisplayPolicy.isConsumerBrand(store));
    }

    @Test
    public void uncuratedProviderRowIsNeitherHoweverItIsLabelled() {
        LitemallBrand fresh = brand(1, false, false);
        assertFalse(BrandDisplayPolicy.isDisplayable(fresh));
        assertFalse(BrandDisplayPolicy.isConsumerBrand(fresh));
    }

    @Test
    public void deletedRowIsNeitherEvenWhenEnabled() {
        LitemallBrand gone = brand(0, true, true);
        assertFalse(BrandDisplayPolicy.isDisplayable(gone));
        assertFalse(BrandDisplayPolicy.isConsumerBrand(gone));
    }

    /** Null-valued columns must DENY, not default open — the gate fails closed. */
    @Test
    public void absentFlagsFailClosed() {
        assertFalse(BrandDisplayPolicy.isDisplayable(brand(0, null, false)),
                "an unset display_enabled is not consent");
        assertFalse(BrandDisplayPolicy.isConsumerBrand(brand(null, true, false)),
                "an unset kind is not a claim of being a consumer brand");
        assertTrue(BrandDisplayPolicy.isDisplayable(brand(0, true, null)),
                "an unset deleted flag means NOT deleted \u2014 it must not deny a curated row");
    }

    @Test
    public void nullRowIsNeither() {
        assertFalse(BrandDisplayPolicy.isDisplayable(null));
        assertFalse(BrandDisplayPolicy.isConsumerBrand(null));
    }
}
