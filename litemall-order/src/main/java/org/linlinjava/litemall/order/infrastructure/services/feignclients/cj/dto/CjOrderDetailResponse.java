package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * CJ Dropshipping {@code shopping/order/getOrderDetail} response envelope (fields we consume):
 * <pre>{ code, result, message, data:{ orderId, orderNum, orderStatus, subStatus,
 *   trackNumber, trackingProvider, logisticName }, requestId }</pre>
 * {@code orderStatus} is CJ's enum: CREATED / IN_CART / UNPAID / UNSHIPPED (subStatus
 * PENDING|PROCESSING) / SHIPPED / DELIVERED / CANCELLED.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CjOrderDetailResponse {

    private int code;
    private boolean result;
    private String message;
    private String requestId;
    private Data data;

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private String orderId;
        private String orderNum;
        private String orderStatus;
        private String subStatus;
        private String trackNumber;
        private String trackingProvider;
        private String logisticName;
    }
}
