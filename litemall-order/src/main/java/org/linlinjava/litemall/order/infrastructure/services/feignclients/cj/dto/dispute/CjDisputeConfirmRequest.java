package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** CJ {@code disputes/disputeConfirmInfo} request: the order + lines being disputed. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CjDisputeConfirmRequest {
    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("productInfoList")
    private List<CjDisputeLine> productInfoList;
}
