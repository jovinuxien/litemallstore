package org.linlinjava.litemall.order.domain.model.valueobjects.enums;

public enum LitemallAfterSaleStatus {


    STATUS_INIT(0, "STATUS_INIT"),
    STATUS_REQUEST(1, "STATUS_REQUEST"),
    STATUS_RECEPT(2, "STATUS_RECEPT"),
    STATUS_REFUND(3, "STATUS_REFUND"),
    STATUS_REJECT(4, "STATUS_REJECT"),
    STATUS_CANCEL(5, "STATUS_CANCEL"),

    TYPE_GOODS_MISS(0, "TYPE_GOODS_MISS"),
    TYPE_GOODS_NEEDLESS(1, "TYPE_GOODS_NEEDLESS"),
    TYPE_GOODS_REQUIRED(2, "TYPE_GOODS_REQUIRED");


    private final Short code;
    private final String displayName;

    LitemallAfterSaleStatus(int code, String displayName) {
        this.code = (short) code;
        this.displayName = displayName;
    }

    public Short getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }
}
