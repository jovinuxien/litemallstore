package org.linlinjava.litemall.wallet.application.util.exception.wallet;

public class LitemallExtractAmountExceededException extends RuntimeException {

    public LitemallExtractAmountExceededException() {
        super("Extract amount exceeds available wallet balance.");
    }

    public LitemallExtractAmountExceededException(String message) {
        super(message);
    }
}
