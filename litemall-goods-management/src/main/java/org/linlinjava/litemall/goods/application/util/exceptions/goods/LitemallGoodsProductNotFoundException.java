package org.linlinjava.litemall.goods.application.util.exceptions.goods;

public class LitemallGoodsProductNotFoundException extends RuntimeException {

    public LitemallGoodsProductNotFoundException(String message) {
        super("Product not found: " + message);
    }

    public LitemallGoodsProductNotFoundException() {
        super("Product not found.");
    }
}
