package org.linlinjava.litemall.order.application.util.exception.order;

/**
 * Client-facing aftersale/RMA rule violation (order not eligible, duplicate open
 * application, wrong owner, illegal status transition). Carries a human-readable
 * reason; the REST layer maps it to a clean errno envelope, never a raw 500 —
 * mirroring {@code LitemallCjDisputeException} in the CJ dispute vertical.
 */
public class LitemallAftersaleException extends RuntimeException {

    public LitemallAftersaleException(String message) {
        super(message);
    }
}
