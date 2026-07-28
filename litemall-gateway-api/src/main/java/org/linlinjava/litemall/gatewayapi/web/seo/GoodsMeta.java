package org.linlinjava.litemall.gatewayapi.web.seo;

/**
 * The slice of the Wave-13 {@code GET /srv/goods/meta/{id}} contract this edge
 * renders into a product page's head. Parsed defensively in
 * {@link SeoMetaClient} — any field the service omits arrives {@code null} and
 * the renderer simply drops the tag that needed it.
 */
public record GoodsMeta(
        String id,
        String name,
        String brief,
        String picUrl,
        String retailPrice,
        String currency,
        boolean onSale,
        String rating,
        Integer reviewCount) {
}
