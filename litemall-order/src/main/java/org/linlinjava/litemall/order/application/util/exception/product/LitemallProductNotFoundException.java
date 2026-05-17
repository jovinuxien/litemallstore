package org.linlinjava.litemall.order.application.util.exception.product;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

public class LitemallProductNotFoundException extends RuntimeException {

    public LitemallProductNotFoundException(String message) {
        super("Product not found: " + message);
    }

    public LitemallProductNotFoundException() {
        super("Product not found.");
    }
}
