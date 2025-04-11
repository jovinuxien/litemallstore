package org.linlinjava.litemall.order.domain.model.valueobjects.enums;

import org.linlinjava.litemall.db.util.GrouponConstant;

public enum LitemallGrouponStatus {

    RULE_STATUS_ON(0, "RULE_STATUS_ON"),
    RULE_STATUS_DOWN_EXPIRE(1, "RULE_STATUS_DOWN_EXPIRE"),
    RULE_STATUS_DOWN_ADMIN(2, "RULE_STATUS_DOWN_ADMIN"),

    STATUS_NONE(0, "STATUS_NONE"),
    STATUS_ON(1, "STATUS_ON"),
    STATUS_SUCCEED(2, "STATUS_SUCCEED" ),
    STATUS_FAIL(3,"STATUS_FAIL");



    private final Short code;
    private final String displayName;

    LitemallGrouponStatus(int code, String displayName) {
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
