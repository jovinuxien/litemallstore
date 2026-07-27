package org.linlinjava.litemall.goods.application.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Wave-12 pricing law: retail = cjCost × margin (default 1.25), NO currency factor.
 * The old ×7.2 usdToCny leftover is deleted — a regression re-introducing it would
 * show up here as a 5.76× blowup.
 */
public class CjPricingTest {

    private CjPricing pricing;

    @BeforeEach
    public void setUp() {
        pricing = new CjPricing(new CJDropshippingConfig());
    }

    @Test
    public void retailIsCostTimesDefaultMargin125() {
        assertEquals(new BigDecimal("12.50"), pricing.retail(new BigDecimal("10.00")));
        // The killed formula would have produced 10 × 7.2 × 2.0 = 144.00.
        assertEquals(new BigDecimal("14.81"), pricing.retail(pricing.parseCost("11.85")));
    }

    @Test
    public void marginIsConfigurable() {
        CJDropshippingConfig config = new CJDropshippingConfig();
        config.getPricing().setMargin(new BigDecimal("1.50"));
        assertEquals(new BigDecimal("15.00"), new CjPricing(config).retail(new BigDecimal("10.00")));
    }

    @Test
    public void rangeStringCollapsesToLowerBound() {
        assertEquals(new BigDecimal("14.71"), pricing.parseCost("14.71 -- 64.38"));
        assertEquals(new BigDecimal("11.85"), pricing.parseCost("11.85"));
    }

    @Test
    public void unparseableOrMissingCostYieldsNullNeverZero() {
        assertNull(pricing.parseCost(null));
        assertNull(pricing.parseCost("   "));
        assertNull(pricing.parseCost("free!"));
        assertNull(pricing.cost(null));
        assertNull(pricing.retail(null));
    }

    @Test
    public void numericCostIsScaledHalfUp() {
        assertEquals(new BigDecimal("11.85"), pricing.cost(11.849));
        assertEquals(new BigDecimal("14.81"), pricing.retail(pricing.cost(11.85)));
    }
}
