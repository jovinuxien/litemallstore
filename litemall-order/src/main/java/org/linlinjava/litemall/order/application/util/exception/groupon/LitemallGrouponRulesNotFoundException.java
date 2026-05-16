package org.linlinjava.litemall.order.application.util.exception.groupon;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;



public class LitemallGrouponRulesNotFoundException extends RuntimeException  {

    public LitemallGrouponRulesNotFoundException(String message) {
        super(message);
    }

    public LitemallGrouponRulesNotFoundException() {
        super("Groupon rules not found.");
    }
}
