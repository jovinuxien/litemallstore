package org.linlinjava.litemall.order.domain.model.agregates;

import org.linlinjava.litemall.order.application.util.exception.order.LitemallAftersaleException;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallAfterSaleStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An aftersale/RMA application on a LOCAL order — refund-only or return-and-refund —
 * with a locally-owned lifecycle (unlike {@link LitemallCjDisputeAggregate}, which
 * projects CJ's remote state). Status codes follow upstream litemall's aftersale
 * table: 1 applied → {2 approved → 3 refunded | 4 rejected | 5 cancelled}. Every
 * transition goes through a guarded method here — the single transition source for
 * aftersale state, mirroring the order aggregate's own status methods.
 */
public class LitemallAftersaleAggregate {

    /** Order states an aftersale can be opened from: money was captured and not yet unwound. */
    private static final java.util.Set<LitemallOrderStatus> APPLICABLE_ORDER_STATES = java.util.Set.of(
            LitemallOrderStatus.PAID, LitemallOrderStatus.SHIPPED,
            LitemallOrderStatus.DELIVERED, LitemallOrderStatus.AUTO_DELIVERED);

    private Integer id;
    private String aftersaleSn;
    private LitemallOrderId orderId;
    private LitemallUserId userId;
    private Short type;
    private String reason;
    private LitemallMoney amount;
    private String[] pictures;
    private String comment;
    private LitemallAfterSaleStatus status;
    private LocalDateTime handleTime;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;

    /**
     * Open an application against an order the requester owns. Guards: ownership
     * (reads as not-found to a non-owner, no existence leak), an order state with
     * captured-but-not-unwound money, and a requested amount within what was paid.
     * A null requested amount means "refund what I paid".
     */
    public static LitemallAftersaleAggregate apply(LitemallOrderAggregate order,
                                                   LitemallUserId requester,
                                                   Short type, String reason,
                                                   BigDecimal requestedAmount,
                                                   String[] pictures, String comment) {
        if (order == null || order.getUserId() == null
                || !order.getUserId().getId().equals(requester.getId())) {
            throw new LitemallAftersaleException("Order not found");
        }
        if (!APPLICABLE_ORDER_STATES.contains(order.getOrderStatus())) {
            throw new LitemallAftersaleException(
                    "This order is not eligible for aftersale in its current state ("
                    + order.getOrderStatus().getDisplayName() + ")");
        }
        BigDecimal paid = order.getActualPrice() == null
                ? BigDecimal.ZERO : order.getActualPrice().getAmount();
        BigDecimal amount = requestedAmount == null ? paid : requestedAmount;
        if (amount.signum() < 0 || amount.compareTo(paid) > 0) {
            throw new LitemallAftersaleException(
                    "Requested refund " + amount + " exceeds the amount paid (" + paid + ")");
        }
        LitemallAftersaleAggregate aftersale = new LitemallAftersaleAggregate();
        aftersale.orderId = order.getOrderId();
        aftersale.userId = requester;
        aftersale.type = type == null ? LitemallAfterSaleStatus.TYPE_GOODS_NEEDLESS.getCode() : type;
        aftersale.reason = reason == null ? "" : reason;
        aftersale.amount = new LitemallMoney(amount);
        aftersale.pictures = pictures == null ? new String[0] : pictures;
        aftersale.comment = comment == null ? "" : comment;
        aftersale.status = LitemallAfterSaleStatus.STATUS_REQUEST;
        aftersale.addTime = LocalDateTime.now();
        aftersale.updateTime = aftersale.addTime;
        return aftersale;
    }

    /** True while the application still needs an admin decision. */
    public boolean isOpen() {
        return status == LitemallAfterSaleStatus.STATUS_REQUEST
                || status == LitemallAfterSaleStatus.STATUS_RECEPT;
    }

    /** Admin accepts the application (applied → approved). The refund follows. */
    public void approve() {
        assertStatus(LitemallAfterSaleStatus.STATUS_REQUEST, "approve");
        this.status = LitemallAfterSaleStatus.STATUS_RECEPT;
        this.handleTime = LocalDateTime.now();
        this.updateTime = this.handleTime;
    }

    /** The approved refund landed via the tender-parity path (approved → refunded). */
    public void markRefunded() {
        assertStatus(LitemallAfterSaleStatus.STATUS_RECEPT, "mark refunded");
        this.status = LitemallAfterSaleStatus.STATUS_REFUND;
        this.updateTime = LocalDateTime.now();
    }

    /** Admin declines the application (applied → rejected). The order keeps its money state. */
    public void reject() {
        assertStatus(LitemallAfterSaleStatus.STATUS_REQUEST, "reject");
        this.status = LitemallAfterSaleStatus.STATUS_REJECT;
        this.handleTime = LocalDateTime.now();
        this.updateTime = this.handleTime;
    }

    /** The customer withdraws a still-undecided application (applied → cancelled). */
    public void cancel(LitemallUserId requester) {
        if (userId == null || !userId.getId().equals(requester.getId())) {
            throw new LitemallAftersaleException("Aftersale not found");
        }
        assertStatus(LitemallAfterSaleStatus.STATUS_REQUEST, "cancel");
        this.status = LitemallAfterSaleStatus.STATUS_CANCEL;
        this.updateTime = LocalDateTime.now();
    }

    private void assertStatus(LitemallAfterSaleStatus expected, String action) {
        if (this.status != expected) {
            throw new LitemallAftersaleException(
                    "Cannot " + action + " an aftersale in state " + describeStatus());
        }
    }

    private String describeStatus() {
        return status == null ? "UNKNOWN" : status.getDisplayName();
    }

    // ---- plain accessors (persistence mapping) ---------------------------------

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getAftersaleSn() {
        return aftersaleSn;
    }

    public void setAftersaleSn(String aftersaleSn) {
        this.aftersaleSn = aftersaleSn;
    }

    public LitemallOrderId getOrderId() {
        return orderId;
    }

    public void setOrderId(LitemallOrderId orderId) {
        this.orderId = orderId;
    }

    public LitemallUserId getUserId() {
        return userId;
    }

    public void setUserId(LitemallUserId userId) {
        this.userId = userId;
    }

    public Short getType() {
        return type;
    }

    public void setType(Short type) {
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LitemallMoney getAmount() {
        return amount;
    }

    public void setAmount(LitemallMoney amount) {
        this.amount = amount;
    }

    public String[] getPictures() {
        return pictures;
    }

    public void setPictures(String[] pictures) {
        this.pictures = pictures;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LitemallAfterSaleStatus getStatus() {
        return status;
    }

    public void setStatus(LitemallAfterSaleStatus status) {
        this.status = status;
    }

    public LocalDateTime getHandleTime() {
        return handleTime;
    }

    public void setHandleTime(LocalDateTime handleTime) {
        this.handleTime = handleTime;
    }

    public LocalDateTime getAddTime() {
        return addTime;
    }

    public void setAddTime(LocalDateTime addTime) {
        this.addTime = addTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
