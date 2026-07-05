package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * CJ {@code disputes/create} request. {@code businessDisputeId} is OUR unique key —
 * CJ dedupes on it, which makes creation retry-safe (the response body itself carries
 * no dispute id; that arrives later via getDisputeList).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CjDisputeCreateRequest {
    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("businessDisputeId")
    private String businessDisputeId;
    @JsonProperty("disputeReasonId")
    private Integer disputeReasonId;
    /** 1=Refund, 2=Reissue. */
    @JsonProperty("expectType")
    private Integer expectType;
    /** 1=Balance, 2=Platform — where CJ settles the merchant-side money. */
    @JsonProperty("refundType")
    private Integer refundType;
    @JsonProperty("messageText")
    private String messageText;
    @JsonProperty("imageUrl")
    private List<String> imageUrl;
    @JsonProperty("videoUrl")
    private List<String> videoUrl;
    @JsonProperty("productInfoList")
    private List<CjDisputeLine> productInfoList;
}
