package org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Input to {@code CjDropshipOrderFacade#placeOrder}: a merchant order number, the CJ variant lines
 * (vid + quantity), and the shipping address. The facade maps this to the CJ {@code createOrder}
 * request. This is the contract the (future) checkout-routing will populate from a CJ-sourced order.
 */
@Data
@Builder
public class CjOrderPlacement {

    private String orderNumber;

    private String customerName;
    private String phone;
    /** Customer contact email — createOrderV2 rejects orders without one (CJ error 3001). */
    private String email;
    private String countryCode;
    private String country;
    private String province;
    private String city;
    private String address;
    private String zip;
    private String remark;

    /**
     * Customer-chosen CJ logistics line from checkout (V52), or null. The facade uses it
     * only when freightCalculate still offers it — else configured default / cheapest.
     */
    private String preferredLogisticName;

    /**
     * Store order amount to declare to CJ, in STORE currency (EUR), or null when unknown.
     * Filled by {@code CjFulfillmentService} as the goods value the customer actually paid
     * ({@code actualPrice − freightPrice − taxPrice}; coupon/group-buy/integral discounts
     * are already inside actualPrice). The facade converts it to CJ's currency and sends
     * it as createOrderV2 {@code shopAmount}; null ⇒ the field is omitted and CJ declares
     * its own amount (plan-cj-declared-amount-2026-11-09.md D1).
     */
    private BigDecimal declaredAmount;

    private List<Line> lines;

    /** One CJ line: the CJ variant id and quantity, plus the optional store-side price facts. */
    @Data
    @Builder
    public static class Line {
        private String vid;
        private int quantity;
        /** Unit price the line was sold at, store currency; null when unknown (field omitted). */
        private BigDecimal unitPrice;
        /** Our {@code litemall_order_goods.id}; null for callers without an order line. */
        private Integer lineId;
    }
}
