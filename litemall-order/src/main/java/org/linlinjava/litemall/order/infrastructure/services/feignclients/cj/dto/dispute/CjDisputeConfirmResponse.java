package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * CJ {@code disputes/disputeConfirmInfo} response: what a dispute over the given lines
 * may claim (max amounts), which expectations are allowed ("1"=refund, "2"=reissue),
 * and the selectable dispute reasons.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CjDisputeConfirmResponse {

    private int code;
    private boolean result;
    private String message;
    private String requestId;
    private Payload data;

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        private String orderId;
        private String orderNumber;
        private BigDecimal maxProductPrice;
        private BigDecimal maxPostage;
        private BigDecimal maxIossTaxAmount;
        private BigDecimal maxIossHandTaxAmount;
        private BigDecimal maxAmount;
        private List<String> expectResultOptionList;
        private List<CjDisputeLine> productInfoList;
        private List<Reason> disputeReasonList;
    }

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Reason {
        private Integer disputeReasonId;
        private String reasonName;
    }
}
