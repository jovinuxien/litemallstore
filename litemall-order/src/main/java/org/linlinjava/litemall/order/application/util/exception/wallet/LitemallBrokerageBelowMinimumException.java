package org.linlinjava.litemall.order.application.util.exception.wallet;

/**
 * A brokerage withdrawal was below {@code litemall_brokerage_min_extract} (Wave 5).
 * Extends {@link IllegalStateException} so the existing wallet REST mapping
 * ({@code WalletHttpResponseUtil}: IllegalStateException → 422) keeps working, while
 * the affiliate controller can catch it for its distinct 661 errno.
 */
public class LitemallBrokerageBelowMinimumException extends IllegalStateException {

    public LitemallBrokerageBelowMinimumException(String message) {
        super(message);
    }
}
