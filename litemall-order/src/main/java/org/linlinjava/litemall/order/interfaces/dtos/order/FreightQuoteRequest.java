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

    /**
     * Shipping address id (Wave 4, optional): resolves the destination province for
     * freight-template region matching. Owner-scoped — an addressId that doesn't belong
     * to the calling user is errno 605. Absent → wildcard ('*') region rows only.
     */
    private Integer addressId;

    /**
     * Cart lines being quoted (Wave 4, optional): needed for template pricing (per-goods
     * temp_id grouping + first/continue units). {@code price} is the cart's unit price —
     * send it so free-rule amount thresholds see variant pricing; omitted → the goods'
     * retail price is used. Absent entirely → the legacy flat quote.
     */
    private List<GoodsItem> items;

    /** CJ cart lines to quote the logistics for (native productId + quantity). Optional. */
    private List<Item> cjItems;

    @Data
    public static class Item {
        private Integer productId;
        private Integer quantity;
    }

    @Data
    public static class GoodsItem {
        private Integer goodsId;
        private Integer quantity;
        /** Unit price of the cart line (optional — retail price fallback). */
        private BigDecimal price;
    }
}
