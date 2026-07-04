package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * CJ Dropshipping {@code logistic/freightCalculate} request: warehouse country, destination
 * country, and the variant lines. CJ returns the logistics lines actually available for this
 * combination — {@code createOrder} rejects any other {@code logisticName} (code 1605001).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CjFreightCalculateRequest {

    @JsonProperty("startCountryCode")
    private String startCountryCode;
    @JsonProperty("endCountryCode")
    private String endCountryCode;
    @JsonProperty("products")
    private List<CjOrderProduct> products;
}
