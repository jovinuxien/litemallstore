package org.linlinjava.litemall.order.interfaces.dtos.cart;

import lombok.Data;

/**
 * Body of {@code POST /srv/cart/items}: identifiers + quantity ONLY.
 *
 * <p>Everything else on a cart line — price, goodsSn, goodsName, picUrl,
 * specifications — is resolved server-side from goods-management (see
 * {@code LitemallOrderOrchestratorService.addToCart}). Those fields used to be
 * accepted here and copied onto the cart row verbatim; they are deliberately
 * absent rather than merely ignored, so a field that looks authoritative but
 * isn't can't be re-trusted by the next reader (Wave 7, Task E0).
 *
 * <p>The buyer's identity comes from the gateway's {@code X-User-Id} header,
 * never from this body.
 */
@Data
public class AddCartItemRequest {
    private Integer goodsId;
    private Integer productId;
    private Integer number;
}
