package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CJProductDataResponse {
    @JsonProperty("code")
    private int code; // Response code, e.g., 200
    @JsonProperty("result")
    private boolean result; // Success or failure, e.g., true
    @JsonProperty("message")
    private String message; // Response message, e.g., "Success"
    @JsonProperty("data")
    private CJProductData data; // Product data
    @JsonProperty("requestId")
    private String requestId; //
}
