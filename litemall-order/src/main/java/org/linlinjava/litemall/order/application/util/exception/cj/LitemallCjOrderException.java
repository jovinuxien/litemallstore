package org.linlinjava.litemall.order.application.util.exception.cj;

/**
 * Raised by the CJ ACL ({@code CjDropshipOrderFacade}) when CJ Dropshipping cannot place an order
 * (CJ unreachable, circuit open, or a {@code result=false} business error such as invalid vid /
 * insufficient balance). Mirrors {@code LitemallGoodsServiceUnavailableException}: propagating it
 * fails the CJ placement cleanly rather than producing a half-placed order.
 */
public class LitemallCjOrderException extends RuntimeException {

    public LitemallCjOrderException(String message) {
        super("CJ order placement failed: " + message);
    }

    public LitemallCjOrderException(String message, Throwable cause) {
        super("CJ order placement failed: " + message, cause);
    }
}
