package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * CJ Dropshipping {@code logistic/freightCalculate} response envelope:
 * <pre>{ code, result, message, data:[ { logisticName, logisticPrice, logisticAging } ], requestId }</pre>
 * An empty/absent {@code data} means CJ offers no line for the product/destination combination.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CjFreightCalculateResponse {

    private int code;
    private boolean result;
    private String message;
    private String requestId;
    private List<Option> data;

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Option {
        private String logisticName;
        private BigDecimal logisticPrice;
        private String logisticAging;
    }
}
