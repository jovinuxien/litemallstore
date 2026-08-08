package org.linlinjava.litemall.gatewayapi.web.seo;

/**
 * The slice of the Wave-20 {@code GET /srv/page/{id}} contract this edge
 * renders into a DIY-page head: the page's display name and the URL of the
 * first image-bearing component (banner {@code config.image}, else the first
 * entry of an image-row's {@code config.images[]}), or {@code null} when the
 * page carries no image. Only ACTIVE pages are served by the endpoint —
 * draft/missing pages answer an errno envelope, which the client maps to
 * empty and the filter to the plain shell.
 */
public record PageMeta(
        String id,
        String name,
        String imageUrl) {
}
