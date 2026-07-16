package org.linlinjava.litemall.order.application.util.exception.wallet;

/**
 * A brokerage withdrawal exceeded the available {@code brokerage_price} — the
 * guarded {@code debitBrokerage} UPDATE matched 0 rows (Wave 5). Extends
 * {@link IllegalStateException} so the existing wallet REST mapping
 * ({@code WalletHttpResponseUtil}: IllegalStateException → 422) keeps working, while
 * the affiliate controller can catch it for its distinct 662 errno.
 */
public class LitemallBrokerageInsufficientException extends IllegalStateException {

    public LitemallBrokerageInsufficientException(String message) {
        super(message);
    }
}
