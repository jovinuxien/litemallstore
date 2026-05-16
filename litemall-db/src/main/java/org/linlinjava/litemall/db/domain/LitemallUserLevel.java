package org.linlinjava.litemall.db.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LitemallUserLevel {

    public static final Boolean IS_DELETED  = Deleted.IS_DELETED.value();
    public static final Boolean NOT_DELETED = Deleted.NOT_DELETED.value();

    private Integer       id;
    /** 用户ID */
    private Integer       userId;
    /** 等级ID(关联litemall_system_user_level) */
    private Integer       levelId;
    /** 等级值快照 */
    private Byte          grade;
    /** 达到该等级时的经验值 */
    private Integer       experience;
    /** 1有效 0失效 */
    private Byte          status;
    /** 升级时间 */
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