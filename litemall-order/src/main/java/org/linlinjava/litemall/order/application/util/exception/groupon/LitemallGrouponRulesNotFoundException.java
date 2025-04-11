package org.linlinjava.litemall.order.application.util.exception.groupon;



public class LitemallGrouponRulesNotFoundException extends RuntimeException  {

    public LitemallGrouponRulesNotFoundException(String message) {
        super(message);
    }

    public LitemallGrouponRulesNotFoundException() {
        super("Groupon rules not found.");
    }
}
