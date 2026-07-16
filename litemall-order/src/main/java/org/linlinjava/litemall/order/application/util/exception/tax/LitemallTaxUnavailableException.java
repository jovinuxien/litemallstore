package org.linlinjava.litemall.order.application.util.exception.tax;

/**
 * Tax is enabled but could not be computed (Wave 7, Task C).
 *
 * <p>Blocks the checkout — deliberately. Every other dependency in this module degrades:
 * goods-management down is a 503 the customer can retry, a coupon service outage is a 503,
 * a printer outage is a log line. Tax is different because the failure is SILENT and
 * PERMANENT: an untaxed order completes, the customer is happy, and the liability is only
 * discovered at filing time when the money is gone.
 *
 * <p>So: never catch this into a zero. 503 (transient/retryable) at the REST layer, like
 * the other {@code *Unavailable} exceptions. Caught outside the transaction (the
 * rollback-only→502 landmine).
 */
public class LitemallTaxUnavailableException extends RuntimeException {

    public LitemallTaxUnavailableException(String message) {
        super(message);
    }

    public LitemallTaxUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
