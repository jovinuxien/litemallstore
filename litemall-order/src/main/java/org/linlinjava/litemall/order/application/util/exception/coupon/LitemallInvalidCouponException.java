package org.linlinjava.litemall.order.application.util.exception.coupon;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

public class LitemallInvalidCouponException extends RuntimeException  {

    public LitemallInvalidCouponException(String message) {
        super("Invalid coupon: " + message);
    }
    public LitemallInvalidCouponException() {
        super("Invalid coupon.");
    }
}
