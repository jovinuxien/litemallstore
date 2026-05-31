package org.linlinjava.litemall.order.application.util.exception.wallet;

public class LitemallWalletNotFoundException extends RuntimeException {

    public LitemallWalletNotFoundException() {
        super("Wallet not found.");
    }

    public LitemallWalletNotFoundException(Integer userId) {
        super("Wallet not found for user ID: " + userId);
    }
}
