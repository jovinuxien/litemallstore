package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * CJ Dropshipping {@code createOrder} request body (CJ API v2). Field names match the CJ contract.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CjCreateOrderRequest {

    /** Merchant order number — also the idempotency key on the CJ side. */
    @JsonProperty("orderNumber")
    private String orderNumber;

    @JsonProperty("shippingCountryCode")
    private String shippingCountryCode;
    @JsonProperty("shippingCountry")
    private String shippingCountry;
    @JsonProperty("shippingProvince")
    private String shippingProvince;
    @JsonProperty("shippingCity")
    private String shippingCity;
    @JsonProperty("shippingAddress")
    private String shippingAddress;
    @JsonProperty("shippingZip")
    private String shippingZip;
    @JsonProperty("shippingCustomerName")
    private String shippingCustomerName;
    @JsonProperty("shippingPhone")
    private String shippingPhone;

    /** Warehouse source country (optional). */
    @JsonProperty("fromCountryCode")
    private String fromCountryCode;
    /** Shipping method, typically chosen from a freight-calculation result (optional). */
    @JsonProperty("logisticName")
    private String logisticName;
    @JsonProperty("remark")
    private String remark;

    @JsonProperty("products")
    private List<CjOrderProduct> products;
}
