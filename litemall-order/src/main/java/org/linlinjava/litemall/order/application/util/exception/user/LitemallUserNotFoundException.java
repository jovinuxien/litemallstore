package org.linlinjava.litemall.order.application.util.exception.user;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

public class LitemallUserNotFoundException extends RuntimeException{


    public LitemallUserNotFoundException(String message) {
        super("Product not found: " + message);
    }

    public LitemallUserNotFoundException() {
        super("Product not found.");
    }
}
