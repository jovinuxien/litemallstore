package org.linlinjava.litemall.wallet.domain.model.valueobjects.enums;

public enum LitemallRechargeType {

    WECHAT("weixin"),
    ALIPAY("alipay"),
    STRIPE("stripe");

    private final String value;

    LitemallRechargeType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static LitemallRechargeType fromValue(String value) {
        for (LitemallRechargeType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown recharge type: " + value);
    }
}