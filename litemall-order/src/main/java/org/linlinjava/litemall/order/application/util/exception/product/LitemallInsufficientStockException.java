package org.linlinjava.litemall.order.application.util.exception.product;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

public class LitemallInsufficientStockException extends RuntimeException{

    /** Full, customer-presentable detail (e.g. the per-product requested/available list). */
    public LitemallInsufficientStockException(String message) {
        super(message);
    }

    public LitemallInsufficientStockException() {
        super("Insufficient stock.");
    }
}
