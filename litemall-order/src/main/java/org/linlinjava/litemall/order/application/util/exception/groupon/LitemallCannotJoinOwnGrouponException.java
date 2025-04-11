package org.linlinjava.litemall.order.application.util.exception.groupon;

public class LitemallCannotJoinOwnGrouponException extends RuntimeException  {

    public LitemallCannotJoinOwnGrouponException(String message) {
        super("Cannot join own groupon: " + message);
    }

    public LitemallCannotJoinOwnGrouponException() {
        super("You cannot join your own groupon.");
    }

    public LitemallCannotJoinOwnGrouponException(String message, Throwable cause) {
        super(message, cause);
    }
}
