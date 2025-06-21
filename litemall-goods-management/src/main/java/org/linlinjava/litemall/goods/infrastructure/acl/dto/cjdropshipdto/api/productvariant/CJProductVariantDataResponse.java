package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productvariant;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class CJProductVariantDataResponse {
    @JsonProperty("code") // Response code, e.g., 200
    private int code; // Response code, e.g., 200
    @JsonProperty("result")
    private boolean result; // Success or failure, e.g., true
    @JsonProperty("message") // Response message, e.g., "Success"
    private String message; // Response message, e.g., "Success"
    @JsonProperty("data")
    private List<CJProductVariantData> data; // Product data
    @JsonProperty("requestId") // Request ID for tracking purposes
    private String requestId;
}
