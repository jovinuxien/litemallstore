package org.linlinjava.litemall.order.interfaces.dtos.order;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Checkout freight/logistics quote input: the cart group's goods subtotal (for the charged
 * freight rule) plus, for CJ carts, the destination country and the CJ lines to quote.
 */
@Data
public class FreightQuoteRequest {

    /** ISO destination country (checkout country picker). Optional — CJ quote skipped without it. */
    private String countryCode;

    /** Goods subtotal of the cart group being quoted; drives the charged-freight rule. */
    private BigDecimal subtotal;

    /** CJ cart lines to quote the logistics for (native productId + quantity). Optional. */
    private List<Item> cjItems;

    @Data
    public static class Item {
        private Integer productId;
        private Integer quantity;
    }
}
