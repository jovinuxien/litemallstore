package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.dispute;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * CJ {@code disputes/disputeProducts} response: the lines of a CJ order that are
 * eligible for a dispute ({@code canChoose}), with CJ's own line references and USD
 * prices — the values create/confirm must echo back.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CjDisputeProductsResponse {

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
        private List<ProductInfo> productInfoList;
    }

    @lombok.Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductInfo {
        private String lineItemId;
        private String cjProductId;
        private String cjVariantId;
        private Boolean canChoose;
        private BigDecimal price;
        private Integer quantity;
        private String cjProductName;
        private String cjImage;
        private String sku;
        private String supplierName;
    }
}
