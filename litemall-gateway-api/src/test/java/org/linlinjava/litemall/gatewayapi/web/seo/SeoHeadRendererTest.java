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
                "https://cf.cjdropshipping.com/pic/abc.jpg", "39.99", "USD", true, "4.6", 12,
                "1036143", "Home, Garden & Furniture");
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
    @DisplayName("off-sale product carries robots noindex; on-sale never does")
    void offSaleNoindex() {
        GoodsMeta offSale = new GoodsMeta("10000553", "Vintage Denim Jacket", "Classic 90s wash denim.",
                null, "39.99", "USD", false, null, null, null, null);
        assertThat(renderer.renderProduct(offSale).orElseThrow())
                .contains("<meta name=\"robots\" content=\"noindex\"");
        assertThat(renderer.renderProduct(meta()).orElseThrow())
                .doesNotContain("\"robots\"");
    }

    @Test
    @DisplayName("noindex shell keeps the template's own title and description")
    void noindexShell() {
        String html = renderer.renderNoindex().orElseThrow();
        assertThat(html).contains("<meta name=\"robots\" content=\"noindex\"");
        assertThat(html).contains("<title>Trovemo</title>");
        assertThat(html).contains("<meta name=\"description\" content=\"Trovemo — online store.\"");
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
        GoodsMeta m = new GoodsMeta("7", "Basic Tee", null, null, "9.99", null, false, null, 0, null, null);
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
                null, null, null, null, true, null, null, null, null);
        String html = renderer.renderProduct(m).orElseThrow();
        assertThat(html).doesNotContain("<script>alert");
        // JSON-LD escapes every '<' so a nested closing tag can't end the block.
        assertThat(html).doesNotContain("</script><script>");
        assertThat(html).contains("&lt;/script&gt;");
    }

    @Test
    @DisplayName("HTML-only brief (CJ supplier markup) ⇒ description falls back to the name")
    void htmlOnlyBrief() {
        GoodsMeta m = new GoodsMeta("11", "Steel Water Bottle",
                "<p><img src=\"/_cdn/oss/product/x.jpg\" style=\"max-width:100%;\" contenteditable=\"false\"/></p>",
                null, "12.50", "USD", true, null, null, null, null);
        String html = renderer.renderProduct(m).orElseThrow();
        assertThat(html).contains("<meta name=\"description\" content=\"Steel Water Bottle\"");
        assertThat(html).contains("<meta property=\"og:description\" content=\"Steel Water Bottle\"");
        assertThat(html).doesNotContain("&lt;p&gt;").doesNotContain("&lt;img");
    }

    @Test
    @DisplayName("mixed HTML brief ⇒ description keeps only the text")
    void mixedHtmlBrief() {
        GoodsMeta m = new GoodsMeta("12", "Desk Lamp",
                "<p>Warm <b>LED</b> light.</p>\n<p><img src=\"x.jpg\"/></p>",
                null, null, null, true, null, null, null, null);
        String html = renderer.renderProduct(m).orElseThrow();
        assertThat(html).contains("<meta name=\"description\" content=\"Warm LED light.\"");
    }

    @Test
    @DisplayName("category: name-driven title, canonical and og:type website")
    void category() {
        String html = renderer.renderCategory(new CategoryMeta("1036007", "Women's Clothing", 42)).orElseThrow();
        assertThat(html).contains("<title>Women&#39;s Clothing | Trovemo</title>");
        assertThat(html).contains("<link rel=\"canonical\" href=\"https://trovemo.com/category/1036007\"");
        assertThat(html).contains("<meta property=\"og:type\" content=\"website\"");
    }

    @Test
    @DisplayName("page (Wave 20): name title, canonical, og:type website + og:site_name")
    void pageBasics() {
        String html = renderer.renderPage(new PageMeta("42", "Coupon spotlight", null)).orElseThrow();
        assertThat(html).contains("<title>Coupon spotlight | Trovemo</title>");
        assertThat(html).contains("<link rel=\"canonical\" href=\"https://trovemo.com/page/42\"");
        assertThat(html).contains("<meta property=\"og:type\" content=\"website\"");
        assertThat(html).contains("<meta property=\"og:site_name\" content=\"Trovemo\"");
        assertThat(html).contains("<meta property=\"og:title\" content=\"Coupon spotlight\"");
        assertThat(html).contains("<meta property=\"og:url\" content=\"https://trovemo.com/page/42\"");
        // No image ⇒ no og:image; the shell's own description is kept, and the
        // bundle/body are untouched (identical HTML for bots and humans).
        assertThat(html).doesNotContain("og:image");
        assertThat(html).contains("<meta name=\"description\" content=\"Trovemo — online store.\"");
        assertThat(html).contains("<script defer src=\"/app/main.abc123.js\"></script>");
        assertThat(html).contains("<div id=\"root\"></div>");
        // Replaced title, never duplicated.
        assertThat(html.split("<title>", -1)).hasSize(2);
    }

    @Test
    @DisplayName("page: relative /_cdn hero image is absolutized against the public base URL")
    void pageRelativeCdnImage() {
        String html = renderer.renderPage(new PageMeta("7", "Group-buy rally", "/_cdn/oss/pic/hero.jpg"))
                .orElseThrow();
        assertThat(html).contains(
                "<meta property=\"og:image\" content=\"https://trovemo.com/_cdn/oss/pic/hero.jpg\"");
        assertThat(html).contains("<meta name=\"twitter:card\" content=\"summary_large_image\"");
    }

    @Test
    @DisplayName("page: CJ-hosted image is swapped to /_cdn and anchored, like products")
    void pageCjImage() {
        String html = renderer.renderPage(
                new PageMeta("7", "Group-buy rally", "https://cf.cjdropshipping.com/pic/abc.jpg")).orElseThrow();
        assertThat(html).contains(
                "<meta property=\"og:image\" content=\"https://trovemo.com/_cdn/cf/pic/abc.jpg\"");
        assertThat(html).doesNotContain("cf.cjdropshipping.com");
    }

    @Test
    @DisplayName("page: hostile names are escaped in the head")
    void pageEscaping() {
        String html = renderer.renderPage(new PageMeta("9", "\"Deals\" & <Steals>", null)).orElseThrow();
        assertThat(html).contains("<meta property=\"og:title\" content=\"&quot;Deals&quot; &amp; &lt;Steals&gt;\"");
        assertThat(html).doesNotContain("content=\"\"Deals\"");
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
        GoodsMeta https = new GoodsMeta("1", "A", null, "https://example.com/x.jpg", null, null, true, null, null, null, null);
        assertThat(renderer.renderProduct(https).orElseThrow())
                .contains("<meta property=\"og:image\" content=\"https://example.com/x.jpg\"");
        GoodsMeta http = new GoodsMeta("2", "B", null, "http://yanxuan.nosdn.127.net/x.jpg", null, null, true, null, null, null, null);
        assertThat(renderer.renderProduct(http).orElseThrow()).doesNotContain("og:image");
    }

    @Test
    @DisplayName("product: breadcrumb runs Home > category > product")
    void productBreadcrumb() {
        String html = renderer.renderProduct(meta()).orElseThrow();
        assertThat(html).contains("\"@type\":\"BreadcrumbList\"");
        assertThat(html).contains("\"position\":1,\"name\":\"Home\",\"item\":\"https://trovemo.com/\"");
        assertThat(html).contains("\"position\":2,\"name\":\"Home, Garden & Furniture\","
                + "\"item\":\"https://trovemo.com/category/1036143\"");
        assertThat(html).contains("\"position\":3,\"name\":\"Vintage Denim Jacket\"");
    }

    @Test
    @DisplayName("product with no category: breadcrumb is skipped, not left sparse")
    void productWithoutCategory() {
        GoodsMeta uncategorised = new GoodsMeta("5", "Orphan", null, null, "1.00", "EUR",
                true, null, null, null, null);
        String html = renderer.renderProduct(uncategorised).orElseThrow();
        assertThat(html).contains("\"position\":2,\"name\":\"Orphan\"");
        assertThat(html).doesNotContain("\"position\":3");
    }

    @Test
    @DisplayName("product offer: condition always, return policy only when configured")
    void offerEnrichment() {
        assertThat(renderer.renderProduct(meta()).orElseThrow())
                .contains("\"itemCondition\":\"https://schema.org/NewCondition\"")
                .doesNotContain("hasMerchantReturnPolicy");

        SeoHeadRenderer configured = new SeoHeadRenderer("https://trovemo.com",
                new SeoHeadRenderer.SiteIdentity("Trovemo", "support@trovemo.com",
                        java.util.List.of("https://www.facebook.com/trovemo"), 30, "SE"),
                () -> SHELL);
        assertThat(configured.renderProduct(meta()).orElseThrow())
                .contains("\"hasMerchantReturnPolicy\"")
                .contains("\"merchantReturnDays\":30")
                .contains("\"applicableCountry\":\"SE\"")
                .contains("\"returnFees\":\"https://schema.org/ReturnShippingFees\"");
    }

    @Test
    @DisplayName("category: emptied by the narrowing ⇒ noindex; populated ⇒ indexable")
    void emptyCategoryNoindex() {
        assertThat(renderer.renderCategory(new CategoryMeta("1005000", "home", 0)).orElseThrow())
                .contains("<meta name=\"robots\" content=\"noindex\"");
        assertThat(renderer.renderCategory(new CategoryMeta("1036143", "Home", 12)).orElseThrow())
                .doesNotContain("\"robots\"");
    }

    @Test
    @DisplayName("home: canonical, og and Organization + WebSite identity")
    void home() {
        SeoHeadRenderer configured = new SeoHeadRenderer("https://trovemo.com",
                new SeoHeadRenderer.SiteIdentity("Trovemo", "support@trovemo.com",
                        java.util.List.of("https://www.facebook.com/trovemo"), 30, "SE"),
                () -> SHELL);
        String html = configured.renderHome("Home, garden and DIY essentials.").orElseThrow();
        assertThat(html).contains("<link rel=\"canonical\" href=\"https://trovemo.com/\"");
        assertThat(html).contains("<meta property=\"og:url\" content=\"https://trovemo.com/\"");
        assertThat(html).contains("<meta property=\"og:title\"");
        assertThat(html).contains("\"@type\":\"Organization\"");
        assertThat(html).contains("\"sameAs\":[\"https://www.facebook.com/trovemo\"]");
        assertThat(html).contains("\"@type\":\"WebSite\"");
        assertThat(html).contains("search?q={search_term_string}");
        assertThat(html).contains("<meta name=\"description\" content=\"Home, garden and DIY essentials.\"");
    }

    @Test
    @DisplayName("home: an unconfigured social profile is absent, never an empty sameAs")
    void homeWithoutSocials() {
        String html = renderer.renderHome("Anything").orElseThrow();
        assertThat(html).contains("\"@type\":\"Organization\"");
        assertThat(html).doesNotContain("sameAs");
        assertThat(html).doesNotContain("\"email\"");
    }
}
