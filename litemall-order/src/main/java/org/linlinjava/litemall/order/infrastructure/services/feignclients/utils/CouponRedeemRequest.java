package org.linlinjava.litemall.order.infrastructure.services.feignclients.utils;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Body of promotion's {@code POST /srv/promotion/coupon/user/{userCouponId}/redeem}.
 * The threshold is re-checked server-side against {@code orderSubtotal}; identity
 * comes from the {@code X-User-Id} header, never the body. {@code goodsIds} and
 * {@code categoryIds} are the cart's scope facts (Wave 18) so promotion can re-check
 * goods scope at consumption — OPTIONAL on the promotion side, so either service can
 * deploy first.
 */
public record CouponRedeemRequest(Integer orderId, BigDecimal orderSubtotal,
                                  Set<Integer> goodsIds, Set<Integer> categoryIds) {
}
