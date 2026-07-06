package org.linlinjava.litemall.order.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the money value object: every amount is normalised to 2dp
 * (half-up), arithmetic stays at 2dp, negatives are rejected, and equals/hashCode
 * are consistent for scale-insensitive equality.
 */
class LitemallMoneyTest {

    @Test
    void constructor_normalisesToTwoDecimalsHalfUp() {
        assertEquals(new BigDecimal("1.24"), new LitemallMoney(new BigDecimal("1.235")).getAmount());
        assertEquals(new BigDecimal("1.00"), new LitemallMoney(new BigDecimal("1")).getAmount());
        assertEquals(new BigDecimal("0.99"), new LitemallMoney(new BigDecimal("0.994")).getAmount());
    }

    @Test
    void addAndSubtract_stayAtTwoDecimals() {
        LitemallMoney sum = new LitemallMoney(new BigDecimal("1.005")).add(new LitemallMoney(new BigDecimal("2.001")));
        // 1.005 -> 1.01 (half-up), 2.001 -> 2.00, sum 3.01
        assertEquals(new BigDecimal("3.01"), sum.getAmount());

        LitemallMoney diff = new LitemallMoney(new BigDecimal("5.00")).subtract(new LitemallMoney(new BigDecimal("1.50")));
        assertEquals(new BigDecimal("3.50"), diff.getAmount());
    }

    @Test
    void constructor_rejectsNegativeAndNull() {
        assertThrows(IllegalArgumentException.class, () -> new LitemallMoney(new BigDecimal("-0.01")));
        assertThrows(IllegalArgumentException.class, () -> new LitemallMoney(null));
    }

    @Test
    void subtract_rejectsOverdraw() {
        assertThrows(IllegalStateException.class,
                () -> new LitemallMoney(new BigDecimal("1.00")).subtract(new LitemallMoney(new BigDecimal("2.00"))));
    }

    @Test
    void equalsAndHashCode_areConsistentForScaleInsensitiveEquality() {
        LitemallMoney a = new LitemallMoney(new BigDecimal("1.5"));   // -> 1.50
        LitemallMoney b = new LitemallMoney(new BigDecimal("1.50"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void isGreaterThanOrEqual_comparesValues() {
        assertTrue(new LitemallMoney(new BigDecimal("2.00")).isGreaterThanOrEqual(new LitemallMoney(new BigDecimal("2.00"))));
        assertTrue(new LitemallMoney(new BigDecimal("2.01")).isGreaterThanOrEqual(new LitemallMoney(new BigDecimal("2.00"))));
    }
}
