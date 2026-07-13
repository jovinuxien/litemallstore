package org.linlinjava.litemall.goods.domain.content;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * Rich-text sanitization for the content subdomain (article {@code content},
 * palette {@code rich-text.html}). Pure domain utility — no Spring, no IO.
 *
 * <p><b>Clean-and-store semantics:</b> {@link Jsoup#clean} never rejects input,
 * it strips what the Safelist disallows; the cleaned result is what gets
 * persisted, so read paths may inject it without re-checking.
 */
public final class HtmlSanitizer {

    /**
     * Custom Safelist (deliberate, per wave-4 plan §3.3 — not a stock preset):
     * {@code relaxed()} as the base (headings, lists, tables, a, img with
     * http/https src...) plus the structural tags rich-text editors emit and
     * {@code a[target,rel]}. Relative hrefs are preserved so content can link
     * SPA routes; protocol-carrying URLs are still whitelist-checked, so
     * {@code javascript:} never survives. No {@code style} attribute (jsoup
     * does not sanitize CSS), no iframes/scripts.
     */
    private static final Safelist SAFELIST = Safelist.relaxed()
            .addTags("div", "span", "figure", "figcaption", "hr", "s")
            .addAttributes("a", "target", "rel")
            .preserveRelativeLinks(true);

    private HtmlSanitizer() {
    }

    /** Null-safe clean; never throws, never rejects. */
    public static String sanitize(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        return Jsoup.clean(html, SAFELIST);
    }
}
