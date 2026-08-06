package org.linlinjava.litemall.promotion.application.ports;

import java.math.BigDecimal;
import java.util.List;

/**
 * Wave-18 margin-guard basis source (goods-management
 * {@code POST /srv/private/admin/insight/margin-basis}, FROZEN spec
 * {@code litemall-goods-management/docs/handoff-coupon-margin-basis.md}).
 * Supplies the worst-margin numbers for a coupon's goods scope; the guard
 * formula and floor live in the promotion application layer.
 */
public interface CouponMarginBasisPort {

    /**
     * Scope basis numbers. Either list may be null/empty; BOTH empty = the
     * whole on-sale catalog (used for ALL-scope coupons).
     *
     * @throws MarginBasisUnavailableException on ANY failure — the caller
     *         must fail CLOSED (never save an unguarded coupon).
     */
    MarginBasis fetch(List<Integer> goodsIds, List<Integer> categoryIds);

    /**
     * Basis numbers per the frozen handoff spec. {@code maxCostRatio} is the
     * WORST cost/retail ratio over the costed in-scope goods — null (never 0)
     * when nothing in scope has a captured cost. {@code minRetailPrice} is
     * null when the scope is empty.
     */
    record MarginBasis(int onSaleCount, int costedCount, int uncostedCount,
                       BigDecimal maxCostRatio, BigDecimal minRetailPrice) {
    }

    /** Typed "guard basis unreachable" — callers must fail CLOSED on it. */
    class MarginBasisUnavailableException extends RuntimeException {
        public MarginBasisUnavailableException(String message) {
            super(message);
        }

        public MarginBasisUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
