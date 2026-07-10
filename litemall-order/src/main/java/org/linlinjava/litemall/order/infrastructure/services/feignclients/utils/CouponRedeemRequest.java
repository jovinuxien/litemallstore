package org.linlinjava.litemall.order.infrastructure.services.feignclients.utils;

import java.math.BigDecimal;

/**
 * Body of promotion's {@code POST /srv/promotion/coupon/user/{userCouponId}/redeem}.
 * The threshold is re-checked server-side against {@code orderSubtotal}; identity
 * comes from the {@code X-User-Id} header, never the body.
 */
public record CouponRedeemRequest(Integer orderId, BigDecimal orderSubtotal) {
}
