package org.linlinjava.litemall.gatewayapi.web.seo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Head injection semantics: what a no-JS crawler sees for a product/category
 * deep link. The template stand-in mirrors the real shell's structure
 * (single-line title + description meta, a hashed bundle tag in place).
 */
class SeoHeadRendererTest {

    private static final String SHELL = """
            <!DOCTYPE html>
            <html lang="en">
              <head>
                <meta charset="utf-8" />
                <meta name="description" content="Trovemo — online store." />
                <title>Trovemo</title>
                <script defer src="/app/main.abc123.js"></script>
              </head>
              <body><div id="root"></div></body>
            </html>
            """;

    private final SeoHeadRenderer renderer = new SeoHeadRenderer("https://trovemo.com", () -> SHELL);

    private static GoodsMeta meta() {
        return new GoodsMeta("10000553", "Vintage Denim Jacket", "Classic 90s wash denim.",
                "https://cf.cjdropshipping.com/pic/abc.jpg", "39.99", "USD", true, "4.6", 12);
    }

    @Test
    @DisplayName("product: title, description and canonical are the product's")
    void productBasics() {
        String html = renderer.renderProduct(meta()).orElseThrow();
        assertThat(html).contains("<title>Vintage Denim Jacket | Trovemo</title>");
        assertThat(html).contains("<meta name=\"description\" content=\"Classic 90s wash denim.\"");
        assertThat(html).contains(
                "<link rel=\"canonical\" href=\"https://trovemo.com/product/10000553-vintage-denim-jacket\"");
        // Exactly one title and one description meta — replaced, not duplicated.
        assertThat(html.split("<title>", -1)).hasSize(2);
        assertThat(html.split("<meta name=\"description\"", -1)).hasSize(2);
        // The body and bundle tags are untouched — humans get the same app.
        assertThat(html).contains("<script defer src=\"/app/main.abc123.js\"></script>");
        assertThat(html).contains("<div id=\"root\"></div>");
    }

    @Test
    @DisplayName("product: OG tags carry an absolute /_cdn image URL")
    void productOpenGraph() {
        String html = renderer.renderProduct(meta()).orElseThrow();
        assertThat(html).contains("<meta property=\"og:type\" content=\"product\"");
        assertThat(html).contains("<meta property=\"og:title\" content=\"Vintage Denim Jacket\"");
        assertThat(html).contains(
                "<meta property=\"og:image\" content=\"https://trovemo.com/_cdn/cf/pic/abc.jpg\"");
        assertThat(html).contains("<meta property=\"product:price:amount\" content=\"39.99\"");
        assertThat(html).contains("<meta name=\"twitter:card\" content=\"summary_large_image\"");
        assertThat(html).doesNotContain("cf.cjdropshipping.com");
    }

    @Test
    @DisplayName("product: JSON-LD has offers and, with reviews, aggregateRating")
    void productJsonLd() {
        String html = renderer.renderProduct(meta()).orElseThrow();
        assertThat(html).contains("<script type=\"application/ld+json\">");
        assertThat(html).contains("\"@type\":\"Product\"");
        assertThat(html).contains("\"price\":\"39.99\"");
        assertThat(html).contains("\"availability\":\"https://schema.org/InStock\"");
        assertThat(html).contains("\"aggregateRating\"");
        assertThat(html).contains("\"reviewCount\":12");
    }

    @Test
    @DisplayName("product: no reviews ⇒ no aggregateRating; off-sale ⇒ OutOfStock")
    void productNoReviewsOffSale() {
        GoodsMeta m = new GoodsMeta("7", "Basic Tee", null, null, "9.99", null, false, null, 0);
        String html = renderer.renderProduct(m).orElseThrow();
        assertThat(html).doesNotContain("aggregateRating");
        assertThat(html).contains("\"availability\":\"https://schema.org/OutOfStock\"");
        // No picUrl ⇒ no og:image and the JSON-LD drops the image field.
        assertThat(html).doesNotContain("og:image");
        // brief missing ⇒ description falls back to the name.
        assertThat(html).contains("<meta name=\"description\" content=\"Basic Tee\"");
    }

    @Test
    @DisplayName("hostile names cannot break out of the head or the JSON-LD script")
    void escaping() {
        GoodsMeta m = new GoodsMeta("9", "</script><script>alert('x')</script> \"Deal\" & <Co>",
                null, null, null, null, true, null, null);
        String html = renderer.renderProduct(m).orElseThrow();
        assertThat(html).doesNotContain("<script>alert");
        // JSON-LD escapes every '<' so a nested closing tag can't end the block.
        assertThat(html).doesNotContain("</script><script>");
        assertThat(html).contains("&lt;/script&gt;");
    }

    @Test
    @DisplayName("category: name-driven title, canonical and og:type website")
    void category() {
        String html = renderer.renderCategory("1036007", "Women's Clothing").orElseThrow();
        assertThat(html).contains("<title>Women&#39;s Clothing | Trovemo</title>");
        assertThat(html).contains("<link rel=\"canonical\" href=\"https://trovemo.com/category/1036007\"");
        assertThat(html).contains("<meta property=\"og:type\" content=\"website\"");
    }

    @Test
    @DisplayName("absent template (unbuilt webapp tree) ⇒ unavailable, renders empty")
    void templateMissing() {
        SeoHeadRenderer unbuilt = new SeoHeadRenderer("https://trovemo.com", () -> {
            throw new IOException("no static/index.html in this tree");
        });
        assertThat(unbuilt.available()).isFalse();
        assertThat(unbuilt.renderProduct(meta())).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("non-CJ absolute https images pass through; http images are dropped")
    void imagePassThrough() {
        GoodsMeta https = new GoodsMeta("1", "A", null, "https://example.com/x.jpg", null, null, true, null, null);
        assertThat(renderer.renderProduct(https).orElseThrow())
                .contains("<meta property=\"og:image\" content=\"https://example.com/x.jpg\"");
        GoodsMeta http = new GoodsMeta("2", "B", null, "http://yanxuan.nosdn.127.net/x.jpg", null, null, true, null, null);
        assertThat(renderer.renderProduct(http).orElseThrow()).doesNotContain("og:image");
    }
}
