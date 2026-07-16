package org.linlinjava.litemall.order.domain.model.valueobjects.enums.payment;

/**
 * How an order was tendered. Deliberately small: every value here has a real integration
 * behind it (Wave 7, Task A4 — payments are Stripe-only).
 *
 * <p>PAYPAL, ALIPAY, WECHAT_PAY, APPLE_PAY, GOOGLE_PAY, BANK_TRANSFER and
 * CASH_ON_DELIVERY were removed. None had any backing integration, none was reachable
 * from the SPA — yet all were accepted by {@code PaymentActionRequest}, because Jackson
 * deserialises straight into this enum with no allowlist, and they routed through the same
 * trust-the-client path that marked an order paid. An enum value that cannot take money
 * but can mark an order paid is a liability, not a placeholder.
 *
 * <p>Verified before removal: no code references them, and no {@code litemall_order.pay_id}
 * row carries them (only WALLET, CREDIT_CARD and the admin OFFLINE marker exist).
 *
 * <p>Note {@code pay_id} can also hold {@code "OFFLINE:<ref>"}, written by
 * {@code adminOfflinePay}. That is intentionally NOT a value here: it is an admin
 * annotation that money arrived out of band, not a tender this service can charge or
 * reverse. {@code paidTender} therefore cannot parse it back, and a refund of such an
 * order is refused rather than invented — see settleRefundToTender.
 */
public enum PaymentMethod {
    CREDIT_CARD("Credit Card"),
    DEBIT_CARD("Debit Card"),
    /** Internal balance; debited server-side, refunded to the same ledger. */
    WALLET("Digital Wallet");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** A Stripe-settled card charge: verified via PaymentIntent, reversed via Refund. */
    public boolean isCard() {
        return this == CREDIT_CARD || this == DEBIT_CARD;
    }
}
