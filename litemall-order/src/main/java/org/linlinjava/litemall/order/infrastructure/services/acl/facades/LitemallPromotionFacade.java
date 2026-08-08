package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.CouponRedemption;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.GroupBuyCampaign;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.GroupBuySlot;
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
     * placement attempt, after the order row exists. The cart's goods/category ids
     * ride along (Wave 18) so promotion re-checks goods scope at consumption —
     * optional on the promotion side. A business rejection (400)
     * throws {@code LitemallInvalidCouponException} — the placement must abort;
     * a transport failure throws {@code LitemallPromotionServiceUnavailableException}.
     */
    CouponRedemption redeemCoupon(LitemallUserId userId, Integer userCouponId,
                                  LitemallOrderId orderId, BigDecimal orderSubtotal,
                                  Set<Integer> goodsIds, Set<Integer> categoryIds);

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

    // ---- combination group-buy (Wave 21, spec-groupon-priced-submit-contract.md) ----

    /**
     * A group-buy slot's state (the queried slot itself, whether leader or member).
     * Empty = promotion does not know the pink (404) — a typed stale-slot reject
     * upstream, never a fall-through to retail.
     *
     * @throws org.linlinjava.litemall.order.application.util.exception.coupon.LitemallPromotionServiceUnavailableException
     *         when promotion cannot be reached — a group submit must fail cleanly (503)
     */
    Optional<GroupBuySlot> findGroupSlot(LitemallUserId userId, Integer pinkId);

    /**
     * A combination campaign definition. Empty = unknown campaign (404); transport
     * failures throw the typed unavailable exception like {@link #findGroupSlot}.
     */
    Optional<GroupBuyCampaign> findCombination(Integer combinationId);

    /**
     * Backfill the slot's {@code order_id} after successful placement. Fail-soft and
     * never throws — promotion ships the endpoint this wave, so a 404 (dev catching
     * up) is tolerated and logged for replay.
     *
     * @return true when promotion confirmed the linkage
     */
    boolean attachOrderToPink(LitemallUserId userId, Integer pinkId, LitemallOrderId orderId);

    /**
     * Free the slot after the consuming order was cancelled before group completion.
     * Idempotent on the promotion side; fail-soft, never throws.
     *
     * @return true when promotion confirmed the release (or it was already released)
     */
    boolean releasePinkSlot(LitemallUserId userId, Integer pinkId, LitemallOrderId orderId);
}
