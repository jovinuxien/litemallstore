package org.linlinjava.litemall.order.domain.model.valueobjects.enums.payment;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

public enum PaymentMethod {
    CREDIT_CARD("Credit Card"),
    DEBIT_CARD("Debit Card"),
    PAYPAL("PayPal"),
    ALIPAY("Alipay"),
    WECHAT_PAY("WeChat Pay"),
    APPLE_PAY("Apple Pay"),
    GOOGLE_PAY("Google Pay"),
    BANK_TRANSFER("Bank Transfer"),
    CASH_ON_DELIVERY("Cash on Delivery"),
    WALLET("Digital Wallet");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isCard() {
        return this == CREDIT_CARD || this == DEBIT_CARD;
    }

    public boolean isDigitalWallet() {
        return this == PAYPAL || this == ALIPAY || this == WECHAT_PAY ||
                this == APPLE_PAY || this == GOOGLE_PAY || this == WALLET;
    }
}
