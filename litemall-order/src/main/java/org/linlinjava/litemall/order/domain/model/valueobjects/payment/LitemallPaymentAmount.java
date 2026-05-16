package org.linlinjava.litemall.order.domain.model.valueobjects.payment;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import lombok.Getter;

import java.math.BigDecimal;
import java.util.Objects;

@Getter
public class LitemallPaymentAmount {

    private final BigDecimal amount;
    private final String currency;
    private final BigDecimal taxAmount;
    private final BigDecimal shippingAmount;
    private final BigDecimal discountAmount;

    public LitemallPaymentAmount(BigDecimal amount, String currency) {
        this(amount, currency, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public LitemallPaymentAmount(BigDecimal amount, String currency, BigDecimal taxAmount,
                         BigDecimal shippingAmount, BigDecimal discountAmount) {
        this.amount = Objects.requireNonNull(amount, "Amount cannot be null");
        this.currency = Objects.requireNonNull(currency, "Currency cannot be null");
        this.taxAmount = taxAmount != null ? taxAmount : BigDecimal.ZERO;
        this.shippingAmount = shippingAmount != null ? shippingAmount : BigDecimal.ZERO;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
    }
}
