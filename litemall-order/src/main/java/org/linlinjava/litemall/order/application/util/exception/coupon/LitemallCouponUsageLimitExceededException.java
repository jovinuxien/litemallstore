package org.linlinjava.litemall.order.application.util.exception.coupon;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

public class LitemallCouponUsageLimitExceededException extends RuntimeException  {

    public LitemallCouponUsageLimitExceededException() {
        super("Coupon usage limit exceeded.");
    }

    public LitemallCouponUsageLimitExceededException(String message) {
        super("Coupon usage limit exceeded: " + message);
    }
}
