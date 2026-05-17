package org.linlinjava.litemall.db.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LitemallUserRecharge {

    public static final Boolean IS_DELETED  = Deleted.IS_DELETED.value();
    public static final Boolean NOT_DELETED = Deleted.NOT_DELETED.value();

    private Integer       id;
    /** 用户ID */
    private Integer       userId;
    /** 充值订单号 */
    private String        orderId;
    /** 充值金额 */
    private BigDecimal    price;
    /** 赠送金额 */
    private BigDecimal    givePrice;
    /** 充值类型 weixin/alipay/stripe */
    private String        rechargeType;
    /** 是否已支付 */
    private Boolean       paid;
    /** 支付时间 */
    private LocalDateTime payTime;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean       deleted;

    public void andLogicalDeleted(boolean deleted) {
        setDeleted(deleted ? Deleted.IS_DELETED.value() : Deleted.NOT_DELETED.value());
    }

    public enum Deleted {
        NOT_DELETED(Boolean.FALSE, "未删除"),
        IS_DELETED(Boolean.TRUE, "已删除");

        private final Boolean value;
        private final String  name;

        Deleted(Boolean value, String name) {
            this.value = value;
            this.name  = name;
        }

        public Boolean value() {
            return this.value;
        }

        public Boolean getValue() {
            return this.value;
        }

        public String getName() {
            return this.name;
        }
    }
}