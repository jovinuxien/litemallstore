package org.linlinjava.litemall.order.application.util.exception.wallet;

public class LitemallInsufficientBalanceException extends RuntimeException {

    public LitemallInsufficientBalanceException() {
        super("Insufficient wallet balance to complete the operation.");
    }

    public LitemallInsufficientBalanceException(String message) {
        super(message);
    }
}
