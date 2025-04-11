package org.linlinjava.litemall.core.infrastructure.acl.dto.cjdropshipdto.api.productdetail;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.linlinjava.litemall.core.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductData;


@Data
public class CJProductDetailResponse {
    @JsonProperty("code") // Response code, e.g., 200
    private int code; // Response code, e.g., 200
    @JsonProperty("result") // Success or failure, e.g., true
    private boolean result; // Success or failure, e.g., true
    @JsonProperty("message") // Response message, e.g., "Success"
    private String message; // Response message, e.g., "Success"
    @JsonProperty("data") // Product data
    private CJProductDetailData data; // Product data
    @JsonProperty("requestId") // Request ID for tracking purposes
    private String requestId;
}
