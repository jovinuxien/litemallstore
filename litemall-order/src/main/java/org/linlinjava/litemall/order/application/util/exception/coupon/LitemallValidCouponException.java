package org.linlinjava.litemall.order.application.util.exception.coupon;

public class LitemallValidCouponException extends RuntimeException{
    public LitemallValidCouponException(String message){
        super("The coupon is still valide " + message);
    }
}
