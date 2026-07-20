package org.linlinjava.litemall.order.application.util.exception.cj;

/**
 * A CJ placement failure that is expected to succeed on a later attempt without any local
 * change: transport errors, circuit-open, auth outages, CJ rate limiting, or an accepted
 * order whose response could not be parsed (reconciled by orderNumber next sweep). The
 * placement sweep keeps retrying these indefinitely — the paid order is retained, never
 * dead-lettered. Contrast with the base {@link LitemallCjOrderException}, which the
 * placement path treats as TERMINAL (a CJ business rejection retrying cannot fix).
 */
public class LitemallCjRetryableException extends LitemallCjOrderException {

    public LitemallCjRetryableException(String message) {
        super(message);
    }

    public LitemallCjRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
