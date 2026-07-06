package org.linlinjava.litemall.order.application.util.exception.cj;

/**
 * A CJ dispute operation could not be completed — a guard failed (order not CJ-fulfilled,
 * not the owner's, already disputed) or CJ rejected/failed the call. Carries the
 * human-readable reason; the REST layer maps it to a clean error envelope.
 */
public class LitemallCjDisputeException extends RuntimeException {

    public LitemallCjDisputeException(String message) {
        super(message);
    }

    public LitemallCjDisputeException(String message, Throwable cause) {
        super(message, cause);
    }
}
