package org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute;

import lombok.Builder;
import lombok.Data;
import org.linlinjava.litemall.order.domain.model.valueobjects.cj.CjDisputeResolution;

import java.math.BigDecimal;

/**
 * CJ's current view of one dispute, in domain language — the projection the local
 * {@code litemall_cj_dispute} row is refreshed from.
 */
@Data
@Builder
public class CjDisputeSnapshot {
    private String cjDisputeId;
    private String status;
    private String reasonName;
    /** Null while CJ has not decided yet. */
    private CjDisputeResolution resolution;
    private BigDecimal refundAmountUsd;
    private String resendOrderCode;
    private String createDate;
}
