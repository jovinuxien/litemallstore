package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** CJ {@code disputes/cancel} request: CJ's order id + CJ's dispute id. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CjDisputeCancelRequest {
    @JsonProperty("orderId")
    private String orderId;
    @JsonProperty("disputeId")
    private String disputeId;
}
