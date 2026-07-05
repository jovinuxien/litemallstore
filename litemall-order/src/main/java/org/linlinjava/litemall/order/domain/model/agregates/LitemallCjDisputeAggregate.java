package org.linlinjava.litemall.order.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjDisputeException;
import org.linlinjava.litemall.order.domain.model.valueobjects.cj.CjDisputeExpectation;
import org.linlinjava.litemall.order.domain.model.valueobjects.cj.CjDisputeResolution;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A CJ Dropshipping dispute the customer raised for one of their {@code source='cj'}
 * orders. Deliberately SMALL (Millett/Tune ch. 19): it references the order by id only,
 * and models just what WE own — the guards for opening/cancelling and the local
 * projection of CJ's state. The dispute lifecycle itself belongs to CJ's context; their
 * status is applied via {@link #applyCjView} rather than re-modelled as a state machine
 * here (supporting subdomain — keep it lean).
 */
@Getter
@Setter
public class LitemallCjDisputeAggregate {

    private Integer disputeId;
    private LitemallOrderId orderId;
    private LitemallUserId userId;
    private String cjOrderId;
    /** Our idempotent merchant key sent to CJ create (unique per dispute). */
    private String businessDisputeId;
    /** CJ's dispute id, back-filled once CJ registers the dispute. */
    private String cjDisputeId;
    private Integer reasonId;
    private String reasonName;
    private CjDisputeExpectation expectation;
    private String message;
    private List<String> imageUrls;
    /** CJ status string as last synced (e.g. "Processing"). */
    private String cjStatus;
    /** CJ's final resolution; null while undecided. */
    private CjDisputeResolution resolution;
    private BigDecimal refundAmountUsd;
    private String resendOrderCode;
    private boolean cancelled;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;

    /**
     * Open-guard: a dispute may be raised only by the order's owner, for a CJ-fulfilled
     * order that has been PAID (money captured and the order placed at CJ) and is not in
     * a pre-payment/cancelled state. Throws {@link LitemallCjDisputeException} with a
     * customer-readable reason otherwise.
     */
    public static void assertDisputable(LitemallOrderAggregate order, LitemallUserId requester) {
        if (order == null || requester == null
                || order.getUserId() == null || !order.getUserId().getId().equals(requester.getId())) {
            throw new LitemallCjDisputeException("Order not found");
        }
        if (!order.isCjFulfilled()) {
            throw new LitemallCjDisputeException(
                    "Order " + order.getOrderSn() + " is not a dropship order; use the normal refund flow instead");
        }
        if (order.getCjOrderId() == null || order.getCjOrderId().isBlank()) {
            throw new LitemallCjDisputeException(
                    "Order " + order.getOrderSn() + " has not been registered at CJ yet; try again shortly");
        }
        LitemallOrderStatus status = order.getOrderStatus();
        boolean paidOrLater = status == LitemallOrderStatus.PAID
                || status == LitemallOrderStatus.SHIPPED
                || status == LitemallOrderStatus.DELIVERED
                || status == LitemallOrderStatus.AUTO_DELIVERED
                || status == LitemallOrderStatus.REFUND_REQUEST;
        if (!paidOrLater) {
            throw new LitemallCjDisputeException(
                    "Order " + order.getOrderSn() + " cannot be disputed in its current state");
        }
    }

    /** True while neither cancelled nor finally resolved by CJ. */
    public boolean isOpen() {
        return !cancelled && resolution == null;
    }

    /** Cancel-guard + transition (the CJ-side cancel is the caller's responsibility). */
    public void markCancelled() {
        if (!isOpen()) {
            throw new LitemallCjDisputeException("Dispute is already closed");
        }
        this.cancelled = true;
        this.updateTime = LocalDateTime.now();
    }

    /** Fold CJ's current view into the local projection (lazy reconciliation). */
    public void applyCjView(String newCjDisputeId, String status, CjDisputeResolution newResolution,
                            BigDecimal refundAmount, String newResendOrderCode) {
        if (newCjDisputeId != null && !newCjDisputeId.isBlank()) {
            this.cjDisputeId = newCjDisputeId;
        }
        if (status != null) {
            this.cjStatus = status;
        }
        if (newResolution != null) {
            this.resolution = newResolution;
        }
        if (refundAmount != null) {
            this.refundAmountUsd = refundAmount;
        }
        if (newResendOrderCode != null) {
            this.resendOrderCode = newResendOrderCode;
        }
        this.updateTime = LocalDateTime.now();
    }
}
