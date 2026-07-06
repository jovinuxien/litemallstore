package org.linlinjava.litemall.order.interfaces.dtos.order;

import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;

import java.time.LocalDateTime;

/**
 * One entry of the order status timeline exposed at {@code GET /srv/order/{id}/timeline}.
 * Carries both the machine-readable status codes and their display labels so the SPA can
 * render a progress trail without hard-coding the status→label map.
 */
public class OrderStatusTimelineDtoResponse {

    private final Short fromStatus;
    private final String fromStatusText;
    private final Short toStatus;
    private final String toStatusText;
    private final String changeType;
    private final String changeMessage;
    private final String operator;
    private final LocalDateTime changeTime;

    private OrderStatusTimelineDtoResponse(Short fromStatus, String fromStatusText,
                                           Short toStatus, String toStatusText,
                                           String changeType, String changeMessage,
                                           String operator, LocalDateTime changeTime) {
        this.fromStatus = fromStatus;
        this.fromStatusText = fromStatusText;
        this.toStatus = toStatus;
        this.toStatusText = toStatusText;
        this.changeType = changeType;
        this.changeMessage = changeMessage;
        this.operator = operator;
        this.changeTime = changeTime;
    }

    public static OrderStatusTimelineDtoResponse fromDomain(LitemallOrderStatusChange c) {
        LitemallOrderStatus from = c.getFromStatus();
        LitemallOrderStatus to = c.getToStatus();
        return new OrderStatusTimelineDtoResponse(
                from == null ? null : from.getCode(),
                from == null ? null : from.getDisplayName(),
                to == null ? null : to.getCode(),
                to == null ? null : to.getDisplayName(),
                c.getChangeType(),
                c.getChangeMessage(),
                c.getOperator(),
                c.getChangeTime());
    }

    public Short getFromStatus() { return fromStatus; }
    public String getFromStatusText() { return fromStatusText; }
    public Short getToStatus() { return toStatus; }
    public String getToStatusText() { return toStatusText; }
    public String getChangeType() { return changeType; }
    public String getChangeMessage() { return changeMessage; }
    public String getOperator() { return operator; }
    public LocalDateTime getChangeTime() { return changeTime; }
}
