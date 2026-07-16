package org.linlinjava.litemall.order.interfaces.dtos.cart;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 *
 * <p>{@code ignoreUnknown} is set EXPLICITLY here rather than relying on
 * litemall-core's {@code JacksonConfig}, whose {@code failOnUnknownProperties(false)}
 * customizer is voided by the plain {@code @Bean ObjectMapper} in the same class (a raw
 * {@code new ObjectMapper()} defaults to failing). Without it, deleting the fields above
 * turns the SPA's existing checkout mirror — which still sends {@code price} — into a hard
 * 402 and breaks the funnel. Ignoring an asserted price is exactly as safe as rejecting it,
 * since the server resolves the real one either way; breaking checkout is not.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddCartItemRequest {
    private Integer goodsId;
    private Integer productId;
    private Integer number;
}
