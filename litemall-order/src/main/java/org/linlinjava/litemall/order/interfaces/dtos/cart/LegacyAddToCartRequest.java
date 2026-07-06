package org.linlinjava.litemall.order.interfaces.dtos.cart;

import lombok.Data;

/**
 * Body of the legacy customer-SPA cart-add ({@code POST /srv/cart/add}). The SPA
 * sends only the identifiers + quantity; the order service enriches name/sn/price/
 * specifications/image from goods-management before persisting the cart line.
 */
@Data
public class LegacyAddToCartRequest {
    private Integer goodsId;
    private Integer productId;
    private Integer number;
}
