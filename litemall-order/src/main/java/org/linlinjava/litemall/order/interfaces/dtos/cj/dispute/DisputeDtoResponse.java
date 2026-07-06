package org.linlinjava.litemall.order.interfaces.dtos.cj.dispute;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCjDisputeAggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One dispute as the customer sees it in My Orders / order detail. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class DisputeDtoResponse {

    private final Integer id;
    private final String businessDisputeId;
    private final String cjDisputeId;
    private final String reasonName;
    /** "REFUND" | "REISSUE" — what the customer asked for. */
    private final String expectType;
    private final String message;
    /** CJ's status string as last synced (e.g. "Processing"). */
    private final String status;
    /** "REFUND" | "REISSUE" | "REJECTED" once CJ decides; absent while open. */
    private final String resolution;
    private final BigDecimal refundAmountUsd;
    private final String resendOrderCode;
    private final boolean cancelled;
    private final boolean open;
    private final LocalDateTime addTime;

    private DisputeDtoResponse(LitemallCjDisputeAggregate d) {
        this.id = d.getDisputeId();
        this.businessDisputeId = d.getBusinessDisputeId();
        this.cjDisputeId = d.getCjDisputeId();
        this.reasonName = d.getReasonName();
        this.expectType = d.getExpectation() == null ? null : d.getExpectation().name();
        this.message = d.getMessage();
        this.status = d.getCjStatus();
        this.resolution = d.getResolution() == null ? null : d.getResolution().name();
        this.refundAmountUsd = d.getRefundAmountUsd();
        this.resendOrderCode = d.getResendOrderCode();
        this.cancelled = d.isCancelled();
        this.open = d.isOpen();
        this.addTime = d.getAddTime();
    }

    public static DisputeDtoResponse fromDomain(LitemallCjDisputeAggregate dispute) {
        return new DisputeDtoResponse(dispute);
    }
}
