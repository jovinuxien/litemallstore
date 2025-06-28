package org.linlinjava.litemall.order.application.util.exception.product;

public class LitemallInsufficientStockException extends RuntimeException{

    public LitemallInsufficientStockException(String goodsProductId) {
        super("Insufficient stock for this id: " + goodsProductId);
    }

    public LitemallInsufficientStockException() {
        super("Insufficient stock.");
    }
}
