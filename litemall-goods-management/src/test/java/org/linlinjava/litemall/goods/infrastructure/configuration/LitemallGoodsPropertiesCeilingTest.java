package org.linlinjava.litemall.goods.infrastructure.configuration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The price-ceiling predicate — mirror of {@link LitemallGoodsPropertiesFloorTest}, and held to
 * the same rules: default OFF, and "absent" is never treated as "above". A null retail
 * (price-locked by a live flash deal, or a good with no captured cost) must not be off-saled by a
 * rule it was never actually measured against.
 *
 * <p>Why the ceiling exists: the live feed on 2026-08-26 carried 97 items over EUR 500, topping
 * out at a EUR 32,819 sideboard. Those come from outlier CJ costs, and on a young merchant
 * account they read as a pricing fault — the kind that triggers an account-level
 * misrepresentation review rather than a single-item rejection.
 */
public class LitemallGoodsPropertiesCeilingTest {

    private LitemallGoodsProperties props(String ceiling) {
        LitemallGoodsProperties p = new LitemallGoodsProperties();
        if (ceiling != null) {
            p.setPriceCeiling(new BigDecimal(ceiling));
        }
        return p;
    }

    @Test
    public void defaultIsOffSoNothingIsEverAboveIt() {
        LitemallGoodsProperties p = new LitemallGoodsProperties();
        assertFalse(p.isAbovePriceCeiling(new BigDecimal("32819.23")),
                "ceiling 0 = OFF: behaviour must be byte-identical to before the ceiling existed");
    }

    @Test
    public void explicitZeroIsAlsoOff() {
        assertFalse(props("0").isAbovePriceCeiling(new BigDecimal("32819.23")));
    }

    @Test
    public void catchesTheImplausiblyPricedTail() {
        LitemallGoodsProperties p = props("500.00");
        assertTrue(p.isAbovePriceCeiling(new BigDecimal("32819.23")), "the EUR 32,819 sideboard");
        assertTrue(p.isAbovePriceCeiling(new BigDecimal("12011.45")), "the EUR 12,011 garage");
        assertTrue(p.isAbovePriceCeiling(new BigDecimal("500.01")));
    }

    @Test
    public void boundaryIsInclusive() {
        LitemallGoodsProperties p = props("500.00");
        assertFalse(p.isAbovePriceCeiling(new BigDecimal("500.00")), "exactly at the ceiling stays on sale");
        assertFalse(p.isAbovePriceCeiling(new BigDecimal("499.99")));
    }

    @Test
    public void scaleDifferencesDoNotChangeTheVerdict() {
        assertFalse(props("500").isAbovePriceCeiling(new BigDecimal("500.00")),
                "compareTo, not equals — 500 and 500.00 must agree");
    }

    @Test
    public void nullRetailIsNeverAboveTheCeiling() {
        assertFalse(props("500.00").isAbovePriceCeiling(null),
                "a price-locked or uncosted good has no measured retail; absent is not above");
    }

    @Test
    public void negativeCeilingIsTreatedAsOff() {
        assertFalse(props("-1").isAbovePriceCeiling(new BigDecimal("999")));
    }

    @Test
    public void floorAndCeilingAreIndependent() {
        // Setting one must not switch the other on — they gate on-sale together, so a stray
        // default here would off-sale the whole catalogue.
        LitemallGoodsProperties floorOnly = new LitemallGoodsProperties();
        floorOnly.setPriceFloor(new BigDecimal("5.00"));
        assertFalse(floorOnly.isAbovePriceCeiling(new BigDecimal("32819.23")));

        LitemallGoodsProperties ceilingOnly = new LitemallGoodsProperties();
        ceilingOnly.setPriceCeiling(new BigDecimal("500.00"));
        assertFalse(ceilingOnly.isBelowPriceFloor(new BigDecimal("0.25")));

        LitemallGoodsProperties both = new LitemallGoodsProperties();
        both.setPriceFloor(new BigDecimal("5.00"));
        both.setPriceCeiling(new BigDecimal("500.00"));
        assertTrue(both.isBelowPriceFloor(new BigDecimal("4.99")));
        assertTrue(both.isAbovePriceCeiling(new BigDecimal("500.01")));
        assertFalse(both.isBelowPriceFloor(new BigDecimal("26.78")), "the median product is in band");
        assertFalse(both.isAbovePriceCeiling(new BigDecimal("26.78")));
    }
}
