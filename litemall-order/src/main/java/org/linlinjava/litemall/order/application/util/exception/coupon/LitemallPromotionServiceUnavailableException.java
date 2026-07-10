package org.linlinjava.litemall.order.application.util.exception.coupon;

/**
 * The promotion service could not be reached (transport error, timeout or open
 * circuit) while the checkout carried a coupon. Placement must fail cleanly in
 * that case — the customer selected a discount, so placing the order without it
 * (or assuming it) is never acceptable. Mapped to HTTP 503 at the REST layer,
 * mirroring {@code LitemallGoodsServiceUnavailableException}.
 */
public class LitemallPromotionServiceUnavailableException extends RuntimeException {

    public LitemallPromotionServiceUnavailableException(String operation, Throwable cause) {
        super("Promotion service unavailable during: " + operation, cause);
    }
}
