package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * CJ Dropshipping create-order request body, shared by legacy {@code createOrder} and
 * {@code createOrderV2}. Field names match the CJ contract; {@code NON_NULL} keeps the
 * V2-only fields ({@code payType}, {@code isSandbox}) off the wire when unset.
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

    /**
     * createOrderV2 payment mode: 1 = pay-URL, 2 = balance auto-deduct, 3 = create-only draft.
     * We send 3 — the draft is placed inside the local pay transaction, so no CJ money may
     * move there (see docs/adr-cj-lifecycle-parity.md); confirm/payBalance follow post-commit.
     */
    @JsonProperty("payType")
    private Integer payType;

    /** createOrderV2 sandbox flag: 1 = CJ simulates payment (no real charges), 0/absent = real. */
    @JsonProperty("isSandbox")
    private Integer isSandbox;

    /**
     * IOSS declaration, required by createOrderV2 for EU destinations (CJ error
     * 100104/7001 without it): 1 = no IOSS (buyer pays import VAT), 2 = own IOSS
     * (+iossNumber), 3 = CJ's IOSS service.
     */
    @JsonProperty("iossType")
    private Integer iossType;

    /** IOSS number, only when {@code iossType=2}. */
    @JsonProperty("iossNumber")
    private String iossNumber;

    @JsonProperty("products")
    private List<CjOrderProduct> products;
}
