package org.linlinjava.litemall.loyalty.domain.model.valueobjects.enums;

import lombok.Getter;

@Getter
public enum LitemallPointsTransactionType {
    EARN_ORDER("pay_order"),
    EARN_SIGN("sign"),
    EARN_ACTIVITY("activity"),
    SPEND_ORDER("use_order"),
    SPEND_ACTIVITY("use_activity");

    private final String mark;

    LitemallPointsTransactionType(String mark) {
        this.mark = mark;
    }
}
