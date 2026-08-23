package org.linlinjava.litemall.gatewayapi.web.seo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    /**
     * The storefront is served in one language today. This states that fact for
     * social scrapers; it is NOT hreflang, which would need genuinely translated
     * URLs to point at and is a content decision, not a markup one.
     */
    private static final String OG_LOCALE = "en_US";
    private static final String HOME_TAGLINE = "Home, Garden & DIY essentials, delivered";
    /** Part of the shipped favicon pack, so it exists on every deploy. */
    private static final String HOME_IMAGE = "/apple-touch-icon.png";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final SiteIdentity site;
    private final Supplier templateSupplier;
    private volatile Optional<String> template;

    /**
     * The facts the homepage's Organization block and a product's return-policy
     * block state about the business. All of it is already published on the
     * site (footer, /returns, seller identity), so this markup only makes
     * machine-readable what shoppers already read — it never asserts anything
     * new. Blank values drop their tag rather than emitting a placeholder.
     */
    public record SiteIdentity(String storeName, String email, List<String> sameAs,
                               int returnDays, String returnCountry) {
        static SiteIdentity defaults() {
            return new SiteIdentity("Trovemo", "", List.of(), 0, "");
        }
    }

    /** How the template is obtained — indirected so tests can inject a string. */
    public interface Supplier {
        String load() throws IOException;
    }

    @Autowired
    public SeoHeadRenderer(
            @Value("${litemall.public-base-url:https://trovemo.com}") String publicBaseUrl,
            @Value("${litemall.seo.store-name:Trovemo}") String storeName,
            @Value("${litemall.seo.contact-email:}") String contactEmail,
            @Value("${litemall.social.facebook-url:}") String facebookUrl,
            @Value("${litemall.social.instagram-url:}") String instagramUrl,
            @Value("${litemall.social.tiktok-url:}") String tiktokUrl,
            @Value("${litemall.social.youtube-url:}") String youtubeUrl,
            @Value("${litemall.social.x-url:}") String xUrl,
            @Value("${litemall.seo.return-days:0}") int returnDays,
            @Value("${litemall.seo.return-country:}") String returnCountry) {
        this(publicBaseUrl,
                new SiteIdentity(storeName, contactEmail,
                        nonBlank(facebookUrl, instagramUrl, tiktokUrl, youtubeUrl, xUrl),
                        returnDays, returnCountry),
                () -> new ClassPathResource(SHELL_RESOURCE)
                        .getContentAsString(StandardCharsets.UTF_8));
    }

    public SeoHeadRenderer(String publicBaseUrl, Supplier templateSupplier) {
        this(publicBaseUrl, SiteIdentity.defaults(), templateSupplier);
    }

    public SeoHeadRenderer(String publicBaseUrl, SiteIdentity site, Supplier templateSupplier) {
        this.baseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        this.site = site;
        this.templateSupplier = templateSupplier;
    }

    private static List<String> nonBlank(String... values) {
        List<String> kept = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                kept.add(value.trim());
            }
        }
        return List.copyOf(kept);
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
            // Retired goods stay viewable-unbuyable, but must leave the index —
            // thousands of off-sale thin PDPs lingering there dilute the live set.
            if (!meta.onSale()) {
                appendMeta(head, "robots", "noindex");
            }
            appendLink(head, "canonical", canonical);
            appendOg(head, "og:type", "product");
            appendOg(head, "og:title", meta.name());
            appendOg(head, "og:description", description);
            appendOg(head, "og:url", canonical);
            appendOg(head, "og:locale", OG_LOCALE);
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
            // Home > Category > Product. The category link is the leaf the goods
            // row carries; the meta contract has served it since Wave 13 and the
            // edge simply never read it.
            appendBreadcrumb(head, crumb(meta.categoryName(), categoryPath(meta.categoryId())),
                    crumb(meta.name(), canonical));

            return apply(shell, title, description, head.toString());
        });
    }

    /**
     * The shell with a category head, or empty when the template is absent.
     *
     * <p>A category the Wave-26 narrowing emptied is a real row with nothing
     * behind it: it must keep answering 200 (restoring the department refills it
     * within a nightly cycle, and a 404 would throw away a URL we intend to use
     * again) but must not be indexed while it shows an empty grid.
     */
    public Optional<String> renderCategory(CategoryMeta category) {
        return template().map(shell -> {
            String categoryName = category.name();
            String canonical = baseUrl + "/category/" + category.id();
            String title = categoryName + " | Trovemo";
            String description = "Shop " + categoryName + " at Trovemo. Browse the "
                    + "full range with fast delivery.";

            StringBuilder head = new StringBuilder();
            if (category.isEmpty()) {
                appendMeta(head, "robots", "noindex");
            }
            appendLink(head, "canonical", canonical);
            appendOg(head, "og:type", "website");
            appendOg(head, "og:title", categoryName);
            appendOg(head, "og:description", description);
            appendOg(head, "og:url", canonical);
            appendOg(head, "og:locale", OG_LOCALE);
            appendMeta(head, "twitter:card", "summary");
            appendMeta(head, "twitter:title", categoryName);
            appendBreadcrumb(head, crumb(categoryName, canonical));

            return apply(shell, title, description, head.toString());
        });
    }

    /**
     * The shell with a homepage head.
     *
     * <p>The homepage was the one crawlable route with no injected head at all:
     * it inherited the template's static tags and therefore had no canonical, no
     * OpenGraph title/url/image, and no entity markup — on the page most likely
     * to be linked to and shared. The Organization and WebSite blocks are what
     * let a search engine tie the domain to the business and its social
     * profiles; {@code sameAs} lists only the profiles actually configured, so
     * an unset channel is simply absent rather than a dead link.
     */
    public Optional<String> renderHome(String description) {
        return template().map(shell -> {
            String canonical = baseUrl + "/";
            String title = site.storeName() + " — " + HOME_TAGLINE;

            StringBuilder head = new StringBuilder();
            appendLink(head, "canonical", canonical);
            appendOg(head, "og:type", "website");
            appendOg(head, "og:title", title);
            appendOg(head, "og:description", description);
            appendOg(head, "og:url", canonical);
            appendOg(head, "og:locale", OG_LOCALE);
            appendOg(head, "og:image", baseUrl + HOME_IMAGE);
            appendMeta(head, "twitter:card", "summary");
            appendMeta(head, "twitter:title", title);
            appendMeta(head, "twitter:description", description);
            head.append("    <script type=\"application/ld+json\">")
                    .append(siteJsonLd(canonical, description))
                    .append("</script>\n");

            return apply(shell, title, description, head.toString());
        });
    }

    /**
     * The shell with a noindex head, for a URL that resolves to nothing.
     *
     * <p>Paired with a 404 status by the caller. The body still hydrates, so a
     * human who mistyped a product id sees the normal storefront while the
     * crawler is told plainly that the URL is not a page.
     */
    public Optional<String> renderNotFound() {
        return renderNoindex();
    }

    /**
     * The shell with a Wave-20 DIY-page head, or empty when the template is
     * absent. Only ACTIVE pages reach this point (the meta client maps the
     * draft/missing errno to empty); the head carries the page name, og:type
     * website + og:site_name, the canonical {@code /page/<id>} URL and — when
     * the page has an image-bearing component — an absolutized og:image (the
     * same {@code /_cdn} swap-and-anchor the product path uses).
     */
    public Optional<String> renderPage(PageMeta meta) {
        return template().map(shell -> {
            String canonical = baseUrl + "/page/" + meta.id();
            String title = meta.name() + " | Trovemo";
            String image = absoluteImageUrl(meta.imageUrl());

            StringBuilder head = new StringBuilder();
            appendLink(head, "canonical", canonical);
            appendOg(head, "og:type", "website");
            appendOg(head, "og:site_name", "Trovemo");
            appendOg(head, "og:title", meta.name());
            appendOg(head, "og:url", canonical);
            if (image != null) {
                appendOg(head, "og:image", image);
            }
            appendMeta(head, "twitter:card", image != null ? "summary_large_image" : "summary");
            appendMeta(head, "twitter:title", meta.name());
            if (image != null) {
                appendMeta(head, "twitter:image", image);
            }

            // No page-level description exists — the shell's own is kept (null).
            return apply(shell, title, null, head.toString());
        });
    }

    /**
     * The shell with only a robots-noindex head — internal search results.
     * Every {@code q} spelling is its own URL over the same generic shell;
     * noindex (rather than a robots.txt disallow) lets Google crawl once, see
     * the directive, and drop any already-indexed copies — a disallow would
     * freeze those in place unseen.
     */
    public Optional<String> renderNoindex() {
        return template().map(shell -> {
            StringBuilder head = new StringBuilder();
            appendMeta(head, "robots", "noindex");
            return apply(shell, null, null, head.toString());
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
     * duplicate for crawlers) — {@code null} keeps the shell's own; the
     * route-specific tags are inserted before {@code </head>}. Each step
     * degrades to a no-op if its marker is missing, so a reworked template can
     * never make this throw.
     */
    private String apply(String shell, String title, String description, String extraHead) {
        String html = title == null ? shell
                : replaceBetween(shell, "<title>", "</title>", escapeHtml(title));
        html = description == null ? html
                : replaceBetween(html, "<meta name=\"description\" content=\"", "\"",
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
        // Every catalogue item is new stock from the supplier; Google treats a
        // missing itemCondition as unknown and suppresses the enrichment.
        offer.put("itemCondition", "https://schema.org/NewCondition");
        Map<String, Object> returns = returnPolicy();
        if (returns != null) {
            offer.put("hasMerchantReturnPolicy", returns);
        }

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
        // <-escape so "</script>" inside a value cannot close the block.
        return writeJson(ld);
    }

    /**
     * The published returns terms, in machine-readable form — the same 30-day
     * window and buyer-pays-return-shipping rule the /returns page states. Emitted
     * only when configured, so an unconfigured environment stays silent rather
     * than promising a policy nobody set.
     */
    private Map<String, Object> returnPolicy() {
        if (site.returnDays() <= 0 || site.returnCountry().isBlank()) {
            return null;
        }
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("@type", "MerchantReturnPolicy");
        policy.put("applicableCountry", site.returnCountry());
        policy.put("returnPolicyCategory", "https://schema.org/MerchantReturnFiniteReturnWindow");
        policy.put("merchantReturnDays", site.returnDays());
        policy.put("returnMethod", "https://schema.org/ReturnByMail");
        // Remorse returns are carried by the buyer — stating it here matches the
        // legal copy exactly; claiming free returns would be a false promise.
        policy.put("returnFees", "https://schema.org/ReturnShippingFees");
        return policy;
    }

    /** Identity markup for the homepage: who runs this store, and where else it lives. */
    private String siteJsonLd(String canonical, String description) {
        Map<String, Object> organization = new LinkedHashMap<>();
        organization.put("@type", "Organization");
        organization.put("name", site.storeName());
        organization.put("url", canonical);
        organization.put("logo", baseUrl + HOME_IMAGE);
        organization.put("description", truncate(description));
        if (!site.email().isBlank()) {
            organization.put("email", site.email());
        }
        if (!site.sameAs().isEmpty()) {
            organization.put("sameAs", site.sameAs());
        }

        Map<String, Object> target = new LinkedHashMap<>();
        target.put("@type", "EntryPoint");
        target.put("urlTemplate", baseUrl + "/search?q={search_term_string}");
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("@type", "SearchAction");
        action.put("target", target);
        action.put("query-input", "required name=search_term_string");

        Map<String, Object> website = new LinkedHashMap<>();
        website.put("@type", "WebSite");
        website.put("name", site.storeName());
        website.put("url", canonical);
        website.put("potentialAction", action);

        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("@context", "https://schema.org");
        graph.put("@graph", List.of(organization, website));
        return writeJson(graph);
    }

    /** One breadcrumb entry, or null when either half is missing. */
    private static Map<String, Object> crumb(String name, String url) {
        if (name == null || name.isBlank() || url == null) {
            return null;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("item", url);
        return entry;
    }

    private String categoryPath(String categoryId) {
        return categoryId == null || categoryId.isBlank() ? null : baseUrl + "/category/" + categoryId;
    }

    /**
     * A BreadcrumbList rooted at the homepage. Entries that could not be built
     * (a product with no category, say) are skipped, and positions are numbered
     * over what actually survives so the list is never sparse.
     */
    @SafeVarargs
    private void appendBreadcrumb(StringBuilder head, Map<String, Object>... entries) {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(crumb("Home", baseUrl + "/"));
        for (Map<String, Object> entry : entries) {
            if (entry != null) {
                items.add(entry);
            }
        }
        if (items.size() < 2) {
            return;
        }
        List<Map<String, Object>> listItems = new ArrayList<>();
        int position = 1;
        for (Map<String, Object> entry : items) {
            Map<String, Object> listItem = new LinkedHashMap<>();
            listItem.put("@type", "ListItem");
            listItem.put("position", position++);
            listItem.put("name", entry.get("name"));
            listItem.put("item", entry.get("item"));
            listItems.add(listItem);
        }
        Map<String, Object> ld = new LinkedHashMap<>();
        ld.put("@context", "https://schema.org");
        ld.put("@type", "BreadcrumbList");
        ld.put("itemListElement", listItems);
        head.append("    <script type=\"application/ld+json\">")
                .append(writeJson(ld))
                .append("</script>\n");
    }

    /** Serialize, then {@code <}-escape so no value can close the script element. */
    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value).replace("<", "\\u003c");
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
