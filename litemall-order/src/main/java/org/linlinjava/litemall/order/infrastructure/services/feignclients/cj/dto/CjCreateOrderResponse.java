package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * CJ Dropshipping {@code createOrder} response envelope:
 * <pre>{ code, result, message, data:{ orderId, orderNum, ... }, requestId }</pre>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CjCreateOrderResponse {
    private int code;
    private boolean result;
    private String message;
    private Data data;
    private String requestId;

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("orderId")
        private String orderId;
        @JsonProperty("orderNum")
        private String orderNum;
        @JsonProperty("orderStatus")
        private String orderStatus;
    }
}
