package org.linlinjava.litemall.order.domain.model.commands;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class LitemallOrderSubmitResult {

    private final Integer orderId;
    private final String orderSn;
    private final boolean needsPayment;
    private final Integer grouponLinkId;
    private final BigDecimal actualPrice;
    private final LocalDateTime createTime;
    private final LitemallOrderSubmitResultStatus status;


    public enum LitemallOrderSubmitResultStatus {
        SUCCESS, PENDING_PAYMENT, FAILED
    }

    public LitemallOrderSubmitResult(Integer orderId, String orderSn, boolean needsPayment,
                                     Integer grouponLinkId, BigDecimal actualPrice,
                                     LocalDateTime createTime, LitemallOrderSubmitResultStatus  status) {
        this.orderId = orderId;
        this.orderSn = orderSn;
        this.needsPayment = needsPayment;
        this.grouponLinkId = grouponLinkId;
        this.actualPrice = actualPrice;
        this.createTime = createTime;
        this.status = status;
    }

    // Factory methods for common scenarios
    public static LitemallOrderSubmitResult successWithPayment(Integer orderId, String orderSn,
                                                               Integer grouponLinkId, BigDecimal actualPrice) {
        return new LitemallOrderSubmitResult(orderId, orderSn, true, grouponLinkId,
                actualPrice, LocalDateTime.now(), LitemallOrderSubmitResultStatus.PENDING_PAYMENT);
    }

    public static LitemallOrderSubmitResult successWithoutPayment(Integer orderId, String orderSn,
                                                                  Integer grouponLinkId) {
        return new LitemallOrderSubmitResult(orderId, orderSn, false, grouponLinkId,
                BigDecimal.ZERO, LocalDateTime.now(), LitemallOrderSubmitResultStatus.PENDING_PAYMENT);
    }

    public static LitemallOrderSubmitResult failed() {
        return new LitemallOrderSubmitResult(null, null, false, null,
                null, null, LitemallOrderSubmitResultStatus.FAILED);
    }

}
