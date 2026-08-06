package org.linlinjava.litemall.promotion.application.internal;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.promotion.application.internal.CouponMarginGuard.GuardDecision;
import org.linlinjava.litemall.promotion.application.ports.CouponMarginBasisPort;
import org.linlinjava.litemall.promotion.application.ports.CouponMarginBasisPort.MarginBasis;
import org.linlinjava.litemall.promotion.application.ports.CouponMarginBasisPort.MarginBasisUnavailableException;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponDiscountType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponGoodsType;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave-18 guard bounds: flat + percent formulas, stated maxima in rejection
 * messages, null-ratio (uncosted scope) rejection, empty-scope rejection and
 * the FAIL-CLOSED path when goods-management is unreachable. Standard basis
 * fixture: maxCostRatio 0.8, floor 1.05 ⇒ headroom 0.16 (max 16% / $8 off a
 * $50 min — the handoff spec's worked example).
 */
class CouponMarginGuardTest {

    private static final BigDecimal FLOOR = new BigDecimal("1.05");

    private static CouponMarginBasisPort basis(MarginBasis result) {
        return (goodsIds, categoryIds) -> result;
    }

    private static final CouponMarginBasisPort STANDARD_BASIS = basis(
            new MarginBasis(1300, 1180, 120, new BigDecimal("0.8"), new BigDecimal("4.99")));

    private static final CouponMarginBasisPort MUST_NOT_BE_CALLED = (goodsIds, categoryIds) -> {
        throw new AssertionError("margin-basis must not be called for this case");
    };

    private LitemallCouponAggregate.LitemallCouponAggregateBuilder base() {
        return LitemallCouponAggregate.builder()
                .goodsType(LitemallCouponGoodsType.ALL)
                .min(new LitemallMoney(new BigDecimal("50.00")));
    }

    // ---- flat ---------------------------------------------------------

    @Test
    void flatWithinBoundsAllowed_withUncostedWarning() {
        CouponMarginGuard guard = new CouponMarginGuard(STANDARD_BASIS, FLOOR);
        GuardDecision decision = guard.check(base()
                .discount(new LitemallMoney(new BigDecimal("8.00"))).build());
        assertTrue(decision.allowed());
        assertEquals(120, decision.uncostedCount());
    }

    @Test
    void flatOverMaxRejected_statesComputedMaximum() {
        CouponMarginGuard guard = new CouponMarginGuard(STANDARD_BASIS, FLOOR);
        GuardDecision decision = guard.check(base()
                .discount(new LitemallMoney(new BigDecimal("10.00"))).build());
        assertFalse(decision.allowed());
        assertEquals(CouponMarginGuard.CODE_DISCOUNT_EXCEEDS_MAX, decision.code());
        assertTrue(decision.message().contains("8.00"), decision.message());
    }

    @Test
    void flatWithZeroMinRejectsAnyDiscount() {
        CouponMarginGuard guard = new CouponMarginGuard(STANDARD_BASIS, FLOOR);
        GuardDecision decision = guard.check(base()
                .min(new LitemallMoney(BigDecimal.ZERO))
                .discount(new LitemallMoney(new BigDecimal("1.00"))).build());
        assertFalse(decision.allowed());
        assertEquals(CouponMarginGuard.CODE_DISCOUNT_EXCEEDS_MAX, decision.code());
        assertTrue(decision.message().contains("raise the min spend"), decision.message());
    }

    // ---- percent ------------------------------------------------------

    @Test
    void percentWithinBoundsAllowed() {
        CouponMarginGuard guard = new CouponMarginGuard(STANDARD_BASIS, FLOOR);
        GuardDecision decision = guard.check(base()
                .discountType(LitemallCouponDiscountType.PERCENT)
                .discount(new LitemallMoney(new BigDecimal("15"))).build());
        assertTrue(decision.allowed());
    }

    @Test
    void percentOverMaxRejected_statesComputedMaximumRate() {
        CouponMarginGuard guard = new CouponMarginGuard(STANDARD_BASIS, FLOOR);
        GuardDecision decision = guard.check(base()
                .discountType(LitemallCouponDiscountType.PERCENT)
                .discount(new LitemallMoney(new BigDecimal("20"))).build());
        assertFalse(decision.allowed());
        assertEquals(CouponMarginGuard.CODE_DISCOUNT_EXCEEDS_MAX, decision.code());
        assertTrue(decision.message().contains("16.00"), decision.message());
    }

    @Test
    void capIsNotASubstituteForTheRateCheck() {
        CouponMarginGuard guard = new CouponMarginGuard(STANDARD_BASIS, FLOOR);
        GuardDecision decision = guard.check(base()
                .discountType(LitemallCouponDiscountType.PERCENT)
                .discount(new LitemallMoney(new BigDecimal("20")))
                .discountCap(new LitemallMoney(new BigDecimal("2.00"))).build());
        assertFalse(decision.allowed());
        assertEquals(CouponMarginGuard.CODE_DISCOUNT_EXCEEDS_MAX, decision.code());
    }

    @Test
    void percentRateOutOfBoundsRejectedWithoutRemoteCall() {
        CouponMarginGuard guard = new CouponMarginGuard(MUST_NOT_BE_CALLED, FLOOR);
        GuardDecision decision = guard.check(base()
                .discountType(LitemallCouponDiscountType.PERCENT)
                .discount(new LitemallMoney(new BigDecimal("95"))).build());
        assertFalse(decision.allowed());
        assertEquals(CouponMarginGuard.CODE_INVALID_RATE, decision.code());
    }

    // ---- basis edge cases --------------------------------------------

    @Test
    void nullMaxCostRatioRejectedAsUncostedScope() {
        CouponMarginGuard guard = new CouponMarginGuard(
                basis(new MarginBasis(40, 0, 40, null, new BigDecimal("9.99"))), FLOOR);
        GuardDecision decision = guard.check(base()
                .discount(new LitemallMoney(new BigDecimal("1.00"))).build());
        assertFalse(decision.allowed());
        assertEquals(CouponMarginGuard.CODE_SCOPE_UNCOSTED, decision.code());
        assertTrue(decision.message().contains("not yet captured"), decision.message());
    }

    @Test
    void emptyScopeRejected() {
        CouponMarginGuard guard = new CouponMarginGuard(
                basis(new MarginBasis(0, 0, 0, null, null)), FLOOR);
        GuardDecision decision = guard.check(base()
                .discount(new LitemallMoney(new BigDecimal("1.00"))).build());
        assertFalse(decision.allowed());
        assertEquals(CouponMarginGuard.CODE_SCOPE_EMPTY, decision.code());
    }

    @Test
    void scopedCouponWithoutIdsRejectedWithoutRemoteCall() {
        CouponMarginGuard guard = new CouponMarginGuard(MUST_NOT_BE_CALLED, FLOOR);
        GuardDecision decision = guard.check(base()
                .goodsType(LitemallCouponGoodsType.CATEGORY)
                .goodsValue(new Integer[]{})
                .discount(new LitemallMoney(new BigDecimal("1.00"))).build());
        assertFalse(decision.allowed());
        assertEquals(CouponMarginGuard.CODE_SCOPE_EMPTY, decision.code());
    }

    @Test
    void basisUnavailableFailsClosed() {
        CouponMarginBasisPort down = (goodsIds, categoryIds) -> {
            throw new MarginBasisUnavailableException("connection refused");
        };
        CouponMarginGuard guard = new CouponMarginGuard(down, FLOOR);
        GuardDecision decision = guard.check(base()
                .discount(new LitemallMoney(new BigDecimal("1.00"))).build());
        assertFalse(decision.allowed());
        assertEquals(CouponMarginGuard.CODE_GUARD_UNAVAILABLE, decision.code());
        assertTrue(decision.message().contains("NOT saved"), decision.message());
    }

    // ---- scope routing ------------------------------------------------

    @Test
    void categoryScopeSendsCategoryIds_productScopeSendsGoodsIds() {
        List<Integer>[] captured = new List[2];
        CouponMarginBasisPort capturing = (goodsIds, categoryIds) -> {
            captured[0] = goodsIds;
            captured[1] = categoryIds;
            return new MarginBasis(10, 10, 0, new BigDecimal("0.5"), BigDecimal.ONE);
        };
        CouponMarginGuard guard = new CouponMarginGuard(capturing, FLOOR);

        GuardDecision categoryDecision = guard.check(base()
                .goodsType(LitemallCouponGoodsType.CATEGORY)
                .goodsValue(new Integer[]{1300})
                .discount(new LitemallMoney(new BigDecimal("5.00"))).build());
        assertTrue(categoryDecision.allowed());
        assertTrue(captured[0].isEmpty());
        assertEquals(List.of(1300), captured[1]);
        assertNull(categoryDecision.uncostedCount());

        GuardDecision productDecision = guard.check(base()
                .goodsType(LitemallCouponGoodsType.ARRAY)
                .goodsValue(new Integer[]{10008302, null, 10008303})
                .discount(new LitemallMoney(new BigDecimal("5.00"))).build());
        assertTrue(productDecision.allowed());
        assertEquals(List.of(10008302, 10008303), captured[0]);
        assertTrue(captured[1].isEmpty());
    }
}
