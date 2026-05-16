package org.linlinjava.litemall.db.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LitemallUserIntegralRecord {

    public static final Boolean IS_DELETED  = Deleted.IS_DELETED.value();
    public static final Boolean NOT_DELETED = Deleted.NOT_DELETED.value();

    private Integer       id;
    /** 用户ID */
    private Integer       userId;
    /** 关联业务ID */
    private String        linkId;
    /** 关联类型 order/sign/activity等 */
    private String        linkType;
    /** 标题 */
    private String        title;
    /** 积分变动量(正增负减) */
    private Integer       number;
    /** 变动后积分余额 */
    private Integer       balance;
    /** 备注 */
    private String        mark;
    /** 1有效 0无效 */
    private Byte          status;
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
