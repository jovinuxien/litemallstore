package org.linlinjava.litemall.gatewayapi.web.seo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Renders the SPA shell with a real {@code <head>} for crawlable routes: the
 * document social crawlers (which run no JS) and Google's first-wave fetch see
 * for a product or category deep link. The body — and therefore what a human's
 * browser renders after hydration — is byte-identical to the plain shell; only
 * head metadata differs, and it differs by ROUTE, never by caller (no UA
 * cloaking).
 *
 * <p>The template is the <em>built</em> shell ({@code classpath:/static/index.html},
 * emitted by the webpack prod build with the hashed bundle tags), not the
 * source template — the rendered page must load the same JS the plain shell
 * does. In a tree where the webapp was never built the resource is absent and
 * {@link #available()} is false; the fallback filter then serves the plain
 * shell exactly as before, so a backend-only dev loop keeps working.
 *
 * <p>Every dynamic value is HTML-escaped; the JSON-LD block is serialized by
 * Jackson and additionally {@code <}-escaped so no goods name can break out of
 * its {@code <script>} element.
 */
@Component
public class SeoHeadRenderer {

    private static final String SHELL_RESOURCE = "static/index.html";
    private static final int DESCRIPTION_MAX = 300;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final Supplier templateSupplier;
    private volatile Optional<String> template;

    /** How the template is obtained — indirected so tests can inject a string. */
    public interface Supplier {
        String load() throws IOException;
    }

    @Autowired
    public SeoHeadRenderer(@Value("${litemall.public-base-url:https://trovemo.com}") String publicBaseUrl) {
        this(publicBaseUrl, () -> new ClassPathResource(SHELL_RESOURCE)
                .getContentAsString(StandardCharsets.UTF_8));
    }

    public SeoHeadRenderer(String publicBaseUrl, Supplier templateSupplier) {
        this.baseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        this.templateSupplier = templateSupplier;
    }

    public boolean available() {
        return template().isPresent();
    }

    /** The shell with a product head, or empty when the template is absent. */
    public Optional<String> renderProduct(GoodsMeta meta) {
        return template().map(shell -> {
            String canonical = baseUrl + Slugs.productPath(meta.id(), meta.name());
            String title = meta.name() + " | Trovemo";
            // ~half the CJ catalog's briefs are raw supplier HTML (often just an
            // <img> wrapped in a <p>) — strip to text; pure markup ⇒ use the name.
            String description = firstNonBlank(plainText(meta.brief()), meta.name());
            String image = absoluteImageUrl(meta.picUrl());

            StringBuilder head = new StringBuilder();
            appendLink(head, "canonical", canonical);
            appendOg(head, "og:type", "product");
            appendOg(head, "og:title", meta.name());
            appendOg(head, "og:description", description);
            appendOg(head, "og:url", canonical);
            if (image != null) {
                appendOg(head, "og:image", image);
            }
            if (meta.retailPrice() != null) {
                appendOg(head, "product:price:amount", meta.retailPrice());
                appendOg(head, "product:price:currency", currency(meta));
            }
            appendMeta(head, "twitter:card", image != null ? "summary_large_image" : "summary");
            appendMeta(head, "twitter:title", meta.name());
            appendMeta(head, "twitter:description", description);
            if (image != null) {
                appendMeta(head, "twitter:image", image);
            }
            head.append("    <script type=\"application/ld+json\">")
                    .append(productJsonLd(meta, canonical, image, description))
                    .append("</script>\n");

            return apply(shell, title, description, head.toString());
        });
    }

    /** The shell with a category head, or empty when the template is absent. */
    public Optional<String> renderCategory(String categoryId, String categoryName) {
        return template().map(shell -> {
            String canonical = baseUrl + "/category/" + categoryId;
            String title = categoryName + " | Trovemo";
            String description = "Shop " + categoryName + " at Trovemo. Browse the "
                    + "full range with fast delivery.";

            StringBuilder head = new StringBuilder();
            appendLink(head, "canonical", canonical);
            appendOg(head, "og:type", "website");
            appendOg(head, "og:title", categoryName);
            appendOg(head, "og:description", description);
            appendOg(head, "og:url", canonical);
            appendMeta(head, "twitter:card", "summary");
            appendMeta(head, "twitter:title", categoryName);

            return apply(shell, title, description, head.toString());
        });
    }

    private Optional<String> template() {
        Optional<String> t = template;
        if (t == null) {
            try {
                t = Optional.of(templateSupplier.load());
            } catch (IOException | RuntimeException missingOrUnreadable) {
                t = Optional.empty();
            }
            template = t;
        }
        return t;
    }

    /**
     * Title and description are REPLACED (a second description meta would be a
     * duplicate for crawlers); the route-specific tags are inserted before
     * {@code </head>}. Each step degrades to a no-op if its marker is missing,
     * so a reworked template can never make this throw.
     */
    private String apply(String shell, String title, String description, String extraHead) {
        String html = replaceBetween(shell, "<title>", "</title>", escapeHtml(title));
        html = replaceBetween(html, "<meta name=\"description\" content=\"", "\"",
                escapeHtml(truncate(description)));
        int headClose = html.indexOf("</head>");
        if (headClose >= 0) {
            html = html.substring(0, headClose) + extraHead + html.substring(headClose);
        }
        return html;
    }

    private String productJsonLd(GoodsMeta meta, String canonical, String image, String description) {
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("@type", "Offer");
        offer.put("url", canonical);
        if (meta.retailPrice() != null) {
            offer.put("price", meta.retailPrice());
            offer.put("priceCurrency", currency(meta));
        }
        offer.put("availability", meta.onSale()
                ? "https://schema.org/InStock"
                : "https://schema.org/OutOfStock");

        Map<String, Object> ld = new LinkedHashMap<>();
        ld.put("@context", "https://schema.org");
        ld.put("@type", "Product");
        ld.put("name", meta.name());
        ld.put("description", truncate(description));
        if (image != null) {
            ld.put("image", image);
        }
        ld.put("productID", meta.id());
        ld.put("sku", meta.id());
        ld.put("offers", offer);
        if (meta.reviewCount() != null && meta.reviewCount() > 0 && meta.rating() != null) {
            Map<String, Object> rating = new LinkedHashMap<>();
            rating.put("@type", "AggregateRating");
            rating.put("ratingValue", meta.rating());
            rating.put("reviewCount", meta.reviewCount());
            ld.put("aggregateRating", rating);
        }
        try {
            // <-escape so "</script>" inside a value cannot close the block.
            return objectMapper.writeValueAsString(ld).replace("<", "\\u003c");
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * og:image must be ABSOLUTE. CJ-hosted pictures are swapped to the same
     * {@code /_cdn} proxy paths {@code CjImageUrlRewriteFilter} serves the SPA
     * (same swap table), then anchored on the public base URL; other absolute
     * https URLs pass through; anything else is dropped rather than emitted
     * broken.
     */
    private String absoluteImageUrl(String picUrl) {
        if (picUrl == null) {
            return null;
        }
        String url = picUrl
                .replace("https://cf.cjdropshipping.com/", "/_cdn/cf/")
                .replace("http://cf.cjdropshipping.com/", "/_cdn/cf/")
                .replace("https://oss-cf.cjdropshipping.com/", "/_cdn/oss/")
                .replace("http://oss-cf.cjdropshipping.com/", "/_cdn/oss/");
        if (url.startsWith("/")) {
            return baseUrl + url;
        }
        return url.startsWith("https://") ? url : null;
    }

    private static String currency(GoodsMeta meta) {
        return meta.currency() != null ? meta.currency() : "USD";
    }

    private static void appendOg(StringBuilder head, String property, String content) {
        head.append("    <meta property=\"").append(property).append("\" content=\"")
                .append(escapeHtml(content)).append("\" />\n");
    }

    private static void appendMeta(StringBuilder head, String name, String content) {
        head.append("    <meta name=\"").append(name).append("\" content=\"")
                .append(escapeHtml(content)).append("\" />\n");
    }

    private static void appendLink(StringBuilder head, String rel, String href) {
        head.append("    <link rel=\"").append(rel).append("\" href=\"")
                .append(escapeHtml(href)).append("\" />\n");
    }

    private static String replaceBetween(String html, String open, String close, String replacement) {
        int start = html.indexOf(open);
        if (start < 0) {
            return html;
        }
        int end = html.indexOf(close, start + open.length());
        if (end < 0) {
            return html;
        }
        return html.substring(0, start + open.length()) + replacement + html.substring(end);
    }

    private static String truncate(String text) {
        if (text.length() <= DESCRIPTION_MAX) {
            return text;
        }
        return text.substring(0, DESCRIPTION_MAX - 1) + "…";
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    /** Tags removed, whitespace runs collapsed — what a description may contain. */
    private static String plainText(String html) {
        if (html == null) {
            return null;
        }
        return html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
