package org.linlinjava.litemall.goods.infrastructure.configuration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 26 Phase 2 deliverable 2: the price-floor predicate. Default OFF, and "absent" is never
 * treated as "below" — a null retail (price-locked by a live flash deal, or a good with no captured
 * cost) must not be off-saled by a rule it was never actually measured against.
 */
public class LitemallGoodsPropertiesFloorTest {

    private LitemallGoodsProperties props(String floor) {
        LitemallGoodsProperties p = new LitemallGoodsProperties();
        if (floor != null) {
            p.setPriceFloor(new BigDecimal(floor));
        }
        return p;
    }

    @Test
    public void defaultIsOffSoNothingIsEverBelowIt() {
        LitemallGoodsProperties p = new LitemallGoodsProperties();
        assertFalse(p.isBelowPriceFloor(new BigDecimal("0.01")),
                "floor 0 = OFF: behaviour must be byte-identical to pre-Wave-26");
        assertFalse(p.isBelowPriceFloor(BigDecimal.ZERO));
    }

    @Test
    public void explicitZeroIsAlsoOff() {
        assertFalse(props("0").isBelowPriceFloor(new BigDecimal("0.25")));
    }

    @Test
    public void catchesTheSubEuroLongTail() {
        LitemallGoodsProperties p = props("5.00");
        assertTrue(p.isBelowPriceFloor(new BigDecimal("0.25")), "the EUR 0.25 RGB cable");
        assertTrue(p.isBelowPriceFloor(new BigDecimal("4.99")));
    }

    @Test
    public void boundaryIsInclusive() {
        LitemallGoodsProperties p = props("5.00");
        assertFalse(p.isBelowPriceFloor(new BigDecimal("5.00")), "exactly at the floor stays on sale");
        assertFalse(p.isBelowPriceFloor(new BigDecimal("5.01")));
    }

    @Test
    public void scaleDifferencesDoNotChangeTheVerdict() {
        LitemallGoodsProperties p = props("5");
        assertFalse(p.isBelowPriceFloor(new BigDecimal("5.00")),
                "compareTo, not equals — 5 and 5.00 must agree");
    }

    @Test
    public void nullRetailIsNeverBelowTheFloor() {
        assertFalse(props("5.00").isBelowPriceFloor(null),
                "a price-locked or uncosted good has no measured retail; absent is not below");
    }

    @Test
    public void negativeFloorIsTreatedAsOff() {
        assertFalse(props("-1").isBelowPriceFloor(new BigDecimal("0.25")));
    }
}
