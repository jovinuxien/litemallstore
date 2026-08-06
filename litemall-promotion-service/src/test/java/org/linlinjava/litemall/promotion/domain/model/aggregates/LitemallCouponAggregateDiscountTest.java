package org.linlinjava.litemall.promotion.domain.model.aggregates;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponDiscountType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponGoodsType;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave-18 percent math (server-side effective discount) and the scope-match
 * behavior the ancestor expansion builds on.
 */
class LitemallCouponAggregateDiscountTest {

    private LitemallCouponAggregate coupon(LitemallCouponDiscountType type,
                                           String discount, String cap) {
        return LitemallCouponAggregate.builder()
                .discount(new LitemallMoney(new BigDecimal(discount)))
                .discountType(type)
                .discountCap(cap != null ? new LitemallMoney(new BigDecimal(cap)) : null)
                .build();
    }

    @Test
    void flatCouponReturnsConfiguredAmount() {
        BigDecimal effective = coupon(LitemallCouponDiscountType.FLAT, "8.00", null)
                .computeEffectiveDiscount(new LitemallMoney(new BigDecimal("50.00")));
        assertEquals(new BigDecimal("8.00"), effective);
    }

    @Test
    void percentCouponComputesRateOfSubtotal() {
        BigDecimal effective = coupon(LitemallCouponDiscountType.PERCENT, "10", null)
                .computeEffectiveDiscount(new LitemallMoney(new BigDecimal("200.00")));
        assertEquals(new BigDecimal("20.00"), effective);
    }

    @Test
    void percentCouponAppliesCap() {
        BigDecimal effective = coupon(LitemallCouponDiscountType.PERCENT, "10", "5.00")
                .computeEffectiveDiscount(new LitemallMoney(new BigDecimal("200.00")));
        assertEquals(new BigDecimal("5.00"), effective);
    }

    @Test
    void percentCouponBelowCapKeepsComputedAmount() {
        BigDecimal effective = coupon(LitemallCouponDiscountType.PERCENT, "10", "50.00")
                .computeEffectiveDiscount(new LitemallMoney(new BigDecimal("120.00")));
        assertEquals(new BigDecimal("12.00"), effective);
    }

    @Test
    void percentRoundsHalfUpToCents() {
        // 15% of 33.33 = 4.9995 -> 5.00
        BigDecimal effective = coupon(LitemallCouponDiscountType.PERCENT, "15", null)
                .computeEffectiveDiscount(new LitemallMoney(new BigDecimal("33.33")));
        assertEquals(new BigDecimal("5.00"), effective);
    }

    @Test
    void discountNeverExceedsSubtotal() {
        BigDecimal effective = coupon(LitemallCouponDiscountType.FLAT, "80.00", null)
                .computeEffectiveDiscount(new LitemallMoney(new BigDecimal("30.00")));
        assertEquals(new BigDecimal("30.00"), effective);
    }

    @Test
    void nullDiscountTypeBehavesAsFlat() {
        LitemallCouponAggregate coupon = LitemallCouponAggregate.builder()
                .discount(new LitemallMoney(new BigDecimal("3.00")))
                .discountType(null)
                .build();
        assertFalse(coupon.isPercent());
        assertEquals(new BigDecimal("3.00"),
                coupon.computeEffectiveDiscount(new LitemallMoney(new BigDecimal("10.00"))));
    }

    @Test
    void categoryScopeStillMatchesLiteralIdsOnly() {
        // The aggregate matches ids literally — ancestor expansion happens
        // BEFORE matchesGoods (CouponScopeExpander); this pins that contract.
        LitemallCouponAggregate coupon = LitemallCouponAggregate.builder()
                .goodsType(LitemallCouponGoodsType.CATEGORY)
                .goodsValue(new Integer[]{1300})
                .build();
        assertFalse(coupon.matchesGoods(List.of(10008302), List.of(1301)));
        assertTrue(coupon.matchesGoods(List.of(10008302), List.of(1301, 1300)));
    }
}
