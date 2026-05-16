package org.linlinjava.litemall.goods.application.util.exceptions.goods;

public class LitemallInsufficientStockException extends RuntimeException{

    public LitemallInsufficientStockException(String message) {
        super("Insufficient stock for this id: " + message);
    }

    public LitemallInsufficientStockException() {
        super("Insufficient stock.");
    }
}
