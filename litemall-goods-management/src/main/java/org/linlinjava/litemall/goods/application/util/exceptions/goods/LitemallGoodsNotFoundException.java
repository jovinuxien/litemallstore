package org.linlinjava.litemall.goods.application.util.exception.goods;

public class LitemallGoodsNotFoundException extends RuntimeException  {

    public LitemallGoodsNotFoundException(String message) {
        super("Goods not found: " + message);
    }

    public LitemallGoodsNotFoundException() {
        super("Goods not found.");
    }
}
