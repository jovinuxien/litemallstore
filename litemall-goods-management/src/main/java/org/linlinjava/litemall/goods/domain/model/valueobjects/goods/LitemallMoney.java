package org.linlinjava.litemall.goods.domain.model.valueobjects.goods;

import lombok.Getter;

import java.math.BigDecimal;


@Getter
public class LitemallMoney {

    private final BigDecimal amount;

    public LitemallMoney(BigDecimal amount) {
        if(amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Money amount must be a non-negative value.");
        }
        this.amount = amount;
    }
}
