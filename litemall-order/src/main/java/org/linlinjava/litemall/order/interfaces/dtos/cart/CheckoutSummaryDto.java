package org.linlinjava.litemall.order.interfaces.dtos.cart;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response of {@code GET /srv/cart/checkout} (Wave 7, Task D —
 * docs/handoff-stripe-checkout.md).
 *
 * <p>Field names match the {@code CheckoutSummary} type the SPA already declares in
 * {@code cartApi.ts:25-35}, which has been dead code since it was written because the
 * endpoint never existed. The SPA has been reducing its own grand total from cart-carried
 * prices instead — so preview and charge could silently disagree, most visibly at a
 * flash-deal boundary where the customer sees one price and is charged another.
 *
 * <p>Every figure here is produced by the same services the submit path uses, which is the
 * only way to guarantee the two agree. {@code taxPrice} is new to the contract and is
 * 0.00 while tax is disabled.
 */
@Data
public class CheckoutSummaryDto {

    /** Sum of catalog-priced checked lines. */
    private BigDecimal goodsTotalPrice;
    private BigDecimal freightPrice;
    /** US sales tax / EU VAT. 0.00 when tax collection is off. */
    private BigDecimal taxPrice;
    private BigDecimal couponPrice;
    /** goods − coupon + freight + tax. */
    private BigDecimal orderTotalPrice;
    /** The amount that will actually be charged. */
    private BigDecimal actualPrice;
    private List<CheckoutLineDto> checkedGoodsList;
    // NOTE: cartApi.ts's CheckoutSummary also declares availableCouponLength. It is not
    // served here: promotion's facade exposes no "list usable coupons" operation (only
    // findUsableCoupon for a specific one), and the SPA already fetches the list directly
    // from /srv/coupon/selectlist. Inventing a count from a facade call that does not
    // exist would be worse than the SPA keeping the source it already has.

    @Data
    public static class CheckoutLineDto {
        private Integer cartId;
        private Integer goodsId;
        private Integer productId;
        private String goodsName;
        private String goodsSn;
        private String picUrl;
        private String[] specifications;
        private Integer number;
        /** The catalog price, server-resolved — never a client assertion. */
        private BigDecimal price;
    }
}
