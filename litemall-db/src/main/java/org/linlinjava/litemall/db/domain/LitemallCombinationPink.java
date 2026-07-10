package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * One participant slot of a combination group-buy (table
 * {@code litemall_combination_pink}, V30). The leader's row has
 * {@code headId = 0}; members carry the leader's row id in {@code headId}.
 * Hand-written, co-located with the generated domains.
 */
public class LitemallCombinationPink {

    private Integer id;
    private Integer combinationId;
    private Integer headId;
    private Integer userId;
    private Integer orderId;
    private Integer requiredMembers;
    private LocalDateTime expireTime;
    /** 0 pending / 1 success / 2 failed. */
    private Integer status;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getCombinationId() { return combinationId; }
    public void setCombinationId(Integer combinationId) { this.combinationId = combinationId; }

    public Integer getHeadId() { return headId; }
    public void setHeadId(Integer headId) { this.headId = headId; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }

    public Integer getRequiredMembers() { return requiredMembers; }
    public void setRequiredMembers(Integer requiredMembers) { this.requiredMembers = requiredMembers; }

    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getAddTime() { return addTime; }
    public void setAddTime(LocalDateTime addTime) { this.addTime = addTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
