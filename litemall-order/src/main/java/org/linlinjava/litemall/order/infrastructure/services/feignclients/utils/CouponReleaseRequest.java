package org.linlinjava.litemall.order.infrastructure.services.feignclients.utils;

/**
 * Body of promotion's {@code POST /srv/promotion/coupon/user/{userCouponId}/release}.
 * Replay-safe: only the exact order that consumed the coupon may release it.
 */
public record CouponReleaseRequest(Integer orderId) {
}
