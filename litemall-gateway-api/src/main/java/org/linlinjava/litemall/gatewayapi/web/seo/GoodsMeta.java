package org.linlinjava.litemall.gatewayapi.web.seo;

/**
 * The slice of the Wave-13 {@code GET /srv/goods/meta/{id}} contract this edge
 * renders into a product page's head. Parsed defensively in
 * {@link SeoMetaClient} — any field the service omits arrives {@code null} and
 * the renderer simply drops the tag that needed it.
 *
 * <p>{@code categoryId}/{@code categoryName} have been in the contract since
 * Wave 13 but were never parsed; they carry the BreadcrumbList that tells
 * Google where a product sits in the store.
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
        Integer reviewCount,
        String categoryId,
        String categoryName) {
}
