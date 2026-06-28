package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * One row of the order status-history audit trail (table {@code litemall_order_status},
 * created in V11, status codes added in V24).
 *
 * <p>Hand-written (not MyBatis-Generator output) and co-located with the generated
 * domains so it is picked up by the same factory. Each row records a single order
 * state transition: {@code oldStatus → newStatus} with a human-readable
 * {@code changeMessage} and the {@code operator} (system/admin/user) that caused it.
 */
public class LitemallOrderStatusLog {

    private Integer id;
    private Integer orderId;
    /** LitemallOrderStatus code before the change; null for the initial "create" row. */
    private Short oldStatus;
    /** LitemallOrderStatus code after the change. */
    private Short newStatus;
    /** Coarse transition keyword: create/pay/ship/receive/auto_receive/cancel/system_cancel/refund_request/refund. */
    private String changeType;
    /** Human-readable note, safe to show to the customer. */
    private String changeMessage;
    private LocalDateTime changeTime;
    /** Who triggered it: "system", "user", or an admin name. */
    private String operator;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }

    public Short getOldStatus() { return oldStatus; }
    public void setOldStatus(Short oldStatus) { this.oldStatus = oldStatus; }

    public Short getNewStatus() { return newStatus; }
    public void setNewStatus(Short newStatus) { this.newStatus = newStatus; }

    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }

    public String getChangeMessage() { return changeMessage; }
    public void setChangeMessage(String changeMessage) { this.changeMessage = changeMessage; }

    public LocalDateTime getChangeTime() { return changeTime; }
    public void setChangeTime(LocalDateTime changeTime) { this.changeTime = changeTime; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public LocalDateTime getAddTime() { return addTime; }
    public void setAddTime(LocalDateTime addTime) { this.addTime = addTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
