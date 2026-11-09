package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The knob parsing the facade tests take for granted. */
class CjDeclaredAmountConverterTest {

    @Test
    void unknownMode_fallsBackToUsd_andLabelsAccordingly() {
        CjDeclaredAmountConverter c = new CjDeclaredAmountConverter(true, "kronor", new BigDecimal("0.866"), "eur");
        assertEquals("usd", c.getMode());
        assertEquals("USD", c.declaredCurrencyLabel());
        assertEquals("EUR", c.storeCurrencyLabel());
        assertEquals(new BigDecimal("10.28"), c.toCjAmount(new BigDecimal("8.90")));
    }

    @Test
    void storeMode_isCaseInsensitive_andLabelsTheStoreCurrency() {
        CjDeclaredAmountConverter c = new CjDeclaredAmountConverter(true, " STORE ", new BigDecimal("0.866"), "eur");
        assertEquals("store", c.getMode());
        assertEquals("EUR", c.declaredCurrencyLabel());
        assertEquals(new BigDecimal("8.90"), c.toCjAmount(new BigDecimal("8.9")));
    }

    @Test
    void nullIn_nullOut_andDisabledIsAlwaysNull() {
        assertNull(new CjDeclaredAmountConverter(true, "usd", BigDecimal.ONE, "eur").toCjAmount(null));
        CjDeclaredAmountConverter off = new CjDeclaredAmountConverter(false, "usd", BigDecimal.ONE, "eur");
        assertNull(off.toCjAmount(new BigDecimal("8.90")));
        assertTrue(!off.isEnabled());
    }

    @Test
    void halfUpAtTwoDecimals_inBothModes() {
        // 1.005 / 1 = 1.005 → 1.01 (HALF_UP), not 1.00 (HALF_EVEN)
        assertEquals(new BigDecimal("1.01"),
                new CjDeclaredAmountConverter(true, "usd", BigDecimal.ONE, "eur").toCjAmount(new BigDecimal("1.005")));
        assertEquals(new BigDecimal("1.01"),
                new CjDeclaredAmountConverter(true, "store", BigDecimal.ONE, "eur").toCjAmount(new BigDecimal("1.005")));
    }
}
