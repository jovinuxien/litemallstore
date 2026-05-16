package org.linlinjava.litemall.db.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LitemallUserExtract {

    public static final Boolean IS_DELETED  = Deleted.IS_DELETED.value();
    public static final Boolean NOT_DELETED = Deleted.NOT_DELETED.value();

    private Integer       id;
    /** 用户ID */
    private Integer       userId;
    /** 提现姓名 */
    private String        realName;
    /** 提现类型 bank/alipay/wechat */
    private String        extractType;
    /** 银行卡号/账号 */
    private String        bankCode;
    /** 开户行 */
    private String        bankAddress;
    /** 提现金额 */
    private BigDecimal    extractPrice;
    /** 提现后余额 */
    private BigDecimal    balance;
    /** -1拒绝 0待审核 1提现中 2已完成 */
    private Byte          status;
    /** 拒绝原因 */
    private String        failMsg;
    /** 拒绝时间 */
    private LocalDateTime failTime;
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
