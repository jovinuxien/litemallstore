package org.linlinjava.litemall.order.application.internal.cj;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 23: mode parsing is FAIL-SAFE — only the literal {@code auto} disables the
 * approval gate; anything else (default, typo, blank env) is manual.
 */
class CjPlacementModeTest {

    @Test
    void auto_disablesTheGate_caseAndWhitespaceTolerant() {
        assertFalse(new CjPlacementMode("auto").isManual());
        assertFalse(new CjPlacementMode(" AUTO ").isManual());
    }

    @Test
    void everythingElse_isManual() {
        assertTrue(new CjPlacementMode("manual").isManual());
        assertTrue(new CjPlacementMode("").isManual());
        assertTrue(new CjPlacementMode(null).isManual());
        assertTrue(new CjPlacementMode("automatic").isManual()); // typo must never arm auto-placement
    }
}
