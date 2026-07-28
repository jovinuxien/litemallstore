package org.linlinjava.litemall.gatewayapi.web.seo;

import java.text.Normalizer;
import java.util.Locale;

/**
 * The Wave-13 slug contract, shared verbatim with the SPA
 * ({@code app/shared/util/slug.ts}) and goods-management's sitemap builder:
 * lowercase, ASCII-fold (NFKD, combining marks stripped), any run of
 * non-alphanumerics becomes a single {@code -}, leading/trailing dashes
 * trimmed, capped at 80 characters.
 *
 * <p>All three implementations must stay byte-identical on the same input —
 * the canonical URL this edge emits has to match the sitemap URL and the
 * hrefs the SPA renders, or crawlers see the same product under competing
 * "canonical" addresses.
 */
public final class Slugs {

    private static final int MAX_LENGTH = 80;

    private Slugs() {
    }

    public static String slug(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String folded = Normalizer.normalize(name, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "");
        String dashed = folded.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        String trimmed = dashed.replaceAll("^-+", "").replaceAll("-+$", "");
        if (trimmed.length() > MAX_LENGTH) {
            trimmed = trimmed.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return trimmed;
    }

    /**
     * Canonical product path: {@code /product/<id>-<slug>}, or the bare id when
     * the name yields no slug (e.g. a fully non-Latin name). Bare-id URLs stay
     * valid forever; this is only what canonical/sitemap/href surfaces emit.
     */
    public static String productPath(String id, String name) {
        String s = slug(name);
        return s.isEmpty() ? "/product/" + id : "/product/" + id + "-" + s;
    }
}
