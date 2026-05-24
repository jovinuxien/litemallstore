package org.linlinjava.litemall.order.application.util.exception.order;

public class LitemallOrderServiceException extends RuntimeException {

    public LitemallOrderServiceException(String message) {
        super(message);
    }

    public LitemallOrderServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
