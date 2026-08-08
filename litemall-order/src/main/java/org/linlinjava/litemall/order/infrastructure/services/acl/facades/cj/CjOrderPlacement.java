package org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj;

import lombok.Builder;
import lombok.Data;

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

    private List<Line> lines;

    /** One CJ line: the CJ variant id and quantity. */
    @Data
    @Builder
    public static class Line {
        private String vid;
        private int quantity;
    }
}
