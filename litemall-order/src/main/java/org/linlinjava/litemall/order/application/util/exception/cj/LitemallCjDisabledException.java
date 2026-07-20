package org.linlinjava.litemall.order.application.util.exception.cj;

/**
 * The CJ ACL is DISABLED because no CJ credentials are configured ({@code CJ_EMAIL} /
 * {@code CJ_API_KEY} empty — prod's state until the user supplies a key). Raised by
 * {@code CjTokenService} instead of attempting a doomed CJ auth call. A subtype of
 * {@link LitemallCjRetryableException}: a paid CJ order hitting this stays retained in
 * the placement sweep's queue and is placed automatically once the key appears — never
 * stranded, never marked fulfilled, never a fake success. Mirrors the Stripe
 * {@code DisabledPaymentGatewayAdapter} disabled-by-honest-degradation pattern.
 */
public class LitemallCjDisabledException extends LitemallCjRetryableException {

    public LitemallCjDisabledException(String message) {
        super(message);
    }
}
