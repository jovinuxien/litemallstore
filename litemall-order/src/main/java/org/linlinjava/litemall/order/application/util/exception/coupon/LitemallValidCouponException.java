package org.linlinjava.litemall.order.application.util.exception.coupon;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

public class LitemallValidCouponException extends RuntimeException{
    public LitemallValidCouponException(String message){
        super("The coupon is still valide " + message);
    }
}
