package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.CouponRedemption;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.UsableCoupon;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

/**
 * Sole adapter between order code and the promotion service's coupon checkout
 * contract (validate / redeem / release —
 * {@code litemall-promotion-service/docs/spec-coupon-checkout-contract.md}).
 * Promotion owns the coupon tables; order never reads them directly. Domain and
 * application code depend on this interface only, never on the Feign client.
 */
public interface LitemallPromotionFacade {

    /**
     * Ask promotion which of the user's coupons apply to this checkout and pick the
     * one the customer selected. The caller supplies the cart facts (subtotal, goods
     * ids, category ids) — promotion has no cart access by design.
     *
     * @return the selected coupon with its authoritative discount, or empty when
     *         promotion does not list it as usable (not owned, expired, below
     *         threshold, or out of goods scope)
     * @throws org.linlinjava.litemall.order.application.util.exception.coupon.LitemallPromotionServiceUnavailableException
     *         when promotion cannot be reached — the caller must fail placement, never
     *         silently drop a discount the customer selected
     */
    Optional<UsableCoupon> findUsableCoupon(LitemallUserId userId, Integer userCouponId,
                                            BigDecimal amount, Set<Integer> goodsIds,
                                            Set<Integer> categoryIds);

    /**
     * Exactly-once redemption (USABLE→USED, stamps this order). Call once per
     * placement attempt, after the order row exists. A business rejection (400)
     * throws {@code LitemallInvalidCouponException} — the placement must abort;
     * a transport failure throws {@code LitemallPromotionServiceUnavailableException}.
     */
    CouponRedemption redeemCoupon(LitemallUserId userId, Integer userCouponId,
                                  LitemallOrderId orderId, BigDecimal orderSubtotal);

    /**
     * Best-effort, idempotent compensation: return a redeemed coupon to USABLE after
     * the consuming placement failed or the order was cancelled. Replay-safe on the
     * promotion side (only the consuming order may release). Never throws.
     *
     * @return true when the coupon is confirmed released (or was already)
     */
    boolean releaseCoupon(LitemallUserId userId, Integer userCouponId, LitemallOrderId orderId);

    /**
     * Find the user's coupon holding that was redeemed by the given order, if any —
     * used by the cancel paths, since the order row does not carry the userCouponId.
     * Best-effort: empty on any failure.
     */
    Optional<Integer> findRedeemedUserCouponForOrder(LitemallUserId userId, LitemallOrderId orderId);
}
