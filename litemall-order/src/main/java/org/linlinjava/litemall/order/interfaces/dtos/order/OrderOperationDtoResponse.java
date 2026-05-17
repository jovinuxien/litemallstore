package org.linlinjava.litemall.order.interfaces.dtos.order;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import com.fasterxml.jackson.annotation.JsonInclude;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.linlinjava.litemall.order.interfaces.dtos.groupon.GrouponInfoDtoResponse;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderOperationDtoResponse {

    private final boolean success;
    private final String status;
    private final String message;
    private final String operationType;
    private final Integer orderId;
    private final String orderSn;
    private final String previousStatus;
    private final String currentStatus;
    private final Boolean paymentRequired;
    private final Double actualPrice;
    private final OrderHandleOptionDtoResponse availableOptions;
    private final GrouponInfoDtoResponse grouponInfo;
    private final LocalDateTime timestamp;
    private final String errorCode;

    // Private constructor
    private OrderOperationDtoResponse(Builder builder) {
        this.success = builder.success;
        this.status = builder.status;
        this.message = builder.message;
        this.operationType = builder.operationType;
        this.orderId = builder.orderId;
        this.orderSn = builder.orderSn;
        this.previousStatus = builder.previousStatus;
        this.currentStatus = builder.currentStatus;
        this.paymentRequired = builder.paymentRequired;
        this.actualPrice = builder.actualPrice;
        this.availableOptions = builder.availableOptions;
        this.grouponInfo = builder.grouponInfo;
        this.timestamp = builder.timestamp;
        this.errorCode = builder.errorCode;
    }

    // Factory method from LitemallOrderOperationResult
    public static OrderOperationDtoResponse fromResult(LitemallOrderOperationResult result) {
        Builder builder = new Builder()
                .success(result.isSuccess())
                .status(result.getOperationType().name())
                .message(result.getMessage())
                .operationType(result.getOperationType().name())
                .timestamp(LocalDateTime.now());

        // Only include order details for successful operations or operations with order context
        if (result.getOrderId() != null) {
            builder.orderId(result.getOrderId().getId());
        }

        if (result.getPreviousStatus() != null) {
            builder.previousStatus(result.getPreviousStatus().name());
        }

        if (result.getNewStatus() != null) {
            builder.currentStatus(result.getNewStatus().name());
        }

        if (result.getAvailableOptions() != null) {
            builder.availableOptions(OrderHandleOptionDtoResponse.fromDomain(result.getAvailableOptions()));
        }

        // Add payment information if relevant
       /* if (result.getOperationType() == LitemallOrderOperationResult.OperationType.SUBMIT ||
                result.getOperationType() == LitemallOrderOperationResult.OperationType.PAY) {
            builder.paymentRequired(result.isPaymentRequired());
        }*/

        // Generate error code for failed operations
        if (!result.isSuccess()) {
            builder.errorCode(generateErrorCode(result));
        }

        return builder.build();
    }

    private static String generateErrorCode(LitemallOrderOperationResult result) {
        // Generate standardized error codes
        return "ORDER_" + result.getOperationType() + "_" +
                (result.getMessage() != null ?
                        result.getMessage().toUpperCase().replace(" ", "_").replace(":", "") : "UNKNOWN");
    }

    // Builder pattern
    public static class Builder {
        private boolean success;
        private String status;
        private String message;
        private String operationType;
        private Integer orderId;
        private String orderSn;
        private String previousStatus;
        private String currentStatus;
        private Boolean paymentRequired;
        private Double actualPrice;
        private OrderHandleOptionDtoResponse availableOptions;
        private GrouponInfoDtoResponse grouponInfo;
        private LocalDateTime timestamp;
        private String errorCode;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder operationType(String operationType) {
            this.operationType = operationType;
            return this;
        }

        public Builder orderId(Integer orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder orderSn(String orderSn) {
            this.orderSn = orderSn;
            return this;
        }

        public Builder previousStatus(String previousStatus) {
            this.previousStatus = previousStatus;
            return this;
        }

        public Builder currentStatus(String currentStatus) {
            this.currentStatus = currentStatus;
            return this;
        }

        public Builder paymentRequired(Boolean paymentRequired) {
            this.paymentRequired = paymentRequired;
            return this;
        }

        public Builder actualPrice(Double actualPrice) {
            this.actualPrice = actualPrice;
            return this;
        }

        public Builder availableOptions(OrderHandleOptionDtoResponse availableOptions) {
            this.availableOptions = availableOptions;
            return this;
        }

        public Builder grouponInfo(GrouponInfoDtoResponse grouponInfo) {
            this.grouponInfo = grouponInfo;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public OrderOperationDtoResponse build() {
            return new OrderOperationDtoResponse(this);
        }
    }
}
