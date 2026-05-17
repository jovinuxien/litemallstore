package org.linlinjava.litemall.order.application.util.exception.product;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

public class LitemallInsufficientStockException extends RuntimeException{

    public LitemallInsufficientStockException(String goodsProductId) {
        super("Insufficient stock for this id: " + goodsProductId);
    }

    public LitemallInsufficientStockException() {
        super("Insufficient stock.");
    }
}
