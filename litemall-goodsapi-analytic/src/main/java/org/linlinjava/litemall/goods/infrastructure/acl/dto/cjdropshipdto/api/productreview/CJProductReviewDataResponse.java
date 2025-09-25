package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productreview;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductData;

@Data
public class CJProductReviewDataResponse {
    @JsonProperty("code")
    private int code; // Response code, e.g., 200
    @JsonProperty("result")
    private boolean result; // Success or failure, e.g., true
    @JsonProperty("message")
    private String message; // Response message, e.g., "Success"
    @JsonProperty("data")
    private CJProductReviewData data; // Product data
    @JsonProperty("requestId")
    private String requestId; //
}
