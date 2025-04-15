package org.linlinjava.litemall.order.application.util.exception.coupon;

public class LitemallInvalidCouponException extends RuntimeException  {

    public LitemallInvalidCouponException(String message) {
        super("Invalid coupon: " + message);
    }
    public LitemallInvalidCouponException() {
        super("Invalid coupon.");
    }
}
