package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * CJ Dropshipping {@code shopping/pay/getBalance} response envelope:
 * <pre>{ code, result, message, data:{ amount, noWithdrawalAmount, freezeAmount }, requestId }</pre>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CjBalanceResponse {

    private int code;
    private boolean result;
    private String message;
    private String requestId;
    private Data data;

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        /** Available balance (USD). */
        private BigDecimal amount;
        /** Bonus funds (non-withdrawable). */
        private BigDecimal noWithdrawalAmount;
        /** Frozen/locked funds. */
        private BigDecimal freezeAmount;
    }
}
