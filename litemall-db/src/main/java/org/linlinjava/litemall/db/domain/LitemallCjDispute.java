package org.linlinjava.litemall.db.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One CJ Dropshipping dispute raised for a {@code source='cj'} order (table
 * {@code litemall_cj_dispute}, created in V28).
 *
 * <p>Hand-written (not MyBatis-Generator output) and co-located with the generated
 * domains, like {@link LitemallOrderStatusLog}. CJ owns the dispute lifecycle; this row
 * is the local projection: our idempotent {@code businessDisputeId}, the CJ ids/status
 * as last synced, and the owner ({@code userId}) every access is scoped to.
 */
public class LitemallCjDispute {

    private Integer id;
    private Integer orderId;
    private Integer userId;
    private String cjOrderId;
    /** OUR deterministic key sent to disputes/create; CJ dedupes on it. */
    private String businessDisputeId;
    /** CJ's dispute id, back-filled from getDisputeList once CJ registers the dispute. */
    private String cjDisputeId;
    private Integer reasonId;
    private String reasonName;
    /** 1=refund, 2=reissue (CJ expectType). */
    private Short expectType;
    private String message;
    /** Comma-separated evidence image URLs. */
    private String imageUrls;
    /** CJ status string as last seen (e.g. "Processing"). */
    private String status;
    /** CJ resolution: 1=refund, 2=reissue, 3=reject; null while undecided. */
    private Short finallyDeal;
    private BigDecimal refundAmount;
    private String resendOrderCode;
    private Boolean cancelled;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getCjOrderId() { return cjOrderId; }
    public void setCjOrderId(String cjOrderId) { this.cjOrderId = cjOrderId; }

    public String getBusinessDisputeId() { return businessDisputeId; }
    public void setBusinessDisputeId(String businessDisputeId) { this.businessDisputeId = businessDisputeId; }

    public String getCjDisputeId() { return cjDisputeId; }
    public void setCjDisputeId(String cjDisputeId) { this.cjDisputeId = cjDisputeId; }

    public Integer getReasonId() { return reasonId; }
    public void setReasonId(Integer reasonId) { this.reasonId = reasonId; }

    public String getReasonName() { return reasonName; }
    public void setReasonName(String reasonName) { this.reasonName = reasonName; }

    public Short getExpectType() { return expectType; }
    public void setExpectType(Short expectType) { this.expectType = expectType; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getImageUrls() { return imageUrls; }
    public void setImageUrls(String imageUrls) { this.imageUrls = imageUrls; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Short getFinallyDeal() { return finallyDeal; }
    public void setFinallyDeal(Short finallyDeal) { this.finallyDeal = finallyDeal; }

    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }

    public String getResendOrderCode() { return resendOrderCode; }
    public void setResendOrderCode(String resendOrderCode) { this.resendOrderCode = resendOrderCode; }

    public Boolean getCancelled() { return cancelled; }
    public void setCancelled(Boolean cancelled) { this.cancelled = cancelled; }

    public LocalDateTime getAddTime() { return addTime; }
    public void setAddTime(LocalDateTime addTime) { this.addTime = addTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
