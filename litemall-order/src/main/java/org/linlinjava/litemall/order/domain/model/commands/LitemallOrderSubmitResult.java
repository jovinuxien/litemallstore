package org.linlinjava.litemall.order.domain.model.commands;

import lombok.Getter;
import org.linlinjava.litemall.db.util.GrouponConstant;

@Getter
public class LitemallOrderSubmitResult {

    private final Integer orderId;
    private final boolean paid;
    private final Integer grouponLinkId;


    public enum LitemallOrderSubmitResultStatus {

        RULE_STATUS_ON(GrouponConstant.RULE_STATUS_ON, "RULE_STATUS_ON"),
        RULE_STATUS_DOWN_EXPIRE(GrouponConstant.RULE_STATUS_DOWN_EXPIRE, "RULE_STATUS_DOWN_EXPIRE"),
        RULE_STATUS_DOWN_ADMIN(GrouponConstant.RULE_STATUS_DOWN_ADMIN, "RULE_STATUS_DOWN_ADMIN"),

        STATUS_NONE(GrouponConstant.STATUS_NONE, "STATUS_NONE"),
        STATUS_ON(GrouponConstant.STATUS_ON, "STATUS_ON"),
        STATUS_SUCCEED(GrouponConstant.STATUS_SUCCEED, "STATUS_SUCCEED" ),
        STATUS_FAIL(GrouponConstant.STATUS_FAIL,"STATUS_FAIL");



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

    public LitemallOrderSubmitResult(Integer orderId, boolean paid, Integer grouponLinkId) {
        this.orderId = orderId;
        this.paid = paid;
        this.grouponLinkId = grouponLinkId;
    }

}
