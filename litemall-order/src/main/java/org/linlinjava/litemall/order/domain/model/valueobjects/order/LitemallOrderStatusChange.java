package org.linlinjava.litemall.order.domain.model.valueobjects.order;

import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;

import java.time.LocalDateTime;

/**
 * An immutable record of a single order state transition, captured by the aggregate
 * when it mutates and persisted to the {@code litemall_order_status} history table in
 * the SAME transaction as the status write (so the timeline can never silently lose a
 * hop the way the best-effort event bus can).
 *
 * <p>{@code fromStatus} is null for the initial "create" entry. {@code operator} is one
 * of "system" / "user" / an admin name.
 */
public final class LitemallOrderStatusChange {

    private final LitemallOrderId orderId;
    private final LitemallOrderStatus fromStatus;
    private final LitemallOrderStatus toStatus;
    private final String changeType;
    private final String changeMessage;
    private final String operator;
    private final LocalDateTime changeTime;

    public LitemallOrderStatusChange(LitemallOrderId orderId,
                                     LitemallOrderStatus fromStatus,
                                     LitemallOrderStatus toStatus,
                                     String changeType,
                                     String changeMessage,
                                     String operator,
                                     LocalDateTime changeTime) {
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changeType = changeType;
        this.changeMessage = changeMessage;
        this.operator = operator;
        this.changeTime = changeTime;
    }

    public LitemallOrderId getOrderId() { return orderId; }
    public LitemallOrderStatus getFromStatus() { return fromStatus; }
    public LitemallOrderStatus getToStatus() { return toStatus; }
    public String getChangeType() { return changeType; }
    public String getChangeMessage() { return changeMessage; }
    public String getOperator() { return operator; }
    public LocalDateTime getChangeTime() { return changeTime; }
}
