package org.linlinjava.litemall.goods.application.seo;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Wave-13 contract slug, deterministic: lowercase, ASCII-fold (NFD + strip combining marks;
 * remaining non-ASCII drops), non-alphanumeric → '-', collapse repeats, trim, max 80 chars.
 * gateway-api carries a TypeScript twin of these rules — change neither side alone.
 *
 * <p>An empty slug is valid (e.g. a CJK-only or all-symbol name): the canonical product URL
 * then degrades to the bare {@code /product/<id>}, which the contract keeps valid forever.
 */
public final class SeoSlugger {

    private static final int MAX_LENGTH = 80;

    private SeoSlugger() {
    }

    public static String slug(String name) {
        if (name == null) {
            return "";
        }
        String folded = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String lower = folded.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        boolean pendingDash = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                if (pendingDash && out.length() > 0) {
                    out.append('-');
                }
                pendingDash = false;
                out.append(c);
            } else {
                // Any separator/symbol/dropped-codepoint run collapses to a single dash,
                // and leading runs are trimmed by the out.length() guard above.
                pendingDash = true;
            }
        }
        String slug = out.toString();
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH);
            while (!slug.isEmpty() && slug.charAt(slug.length() - 1) == '-') {
                slug = slug.substring(0, slug.length() - 1);
            }
        }
        return slug;
    }

    /** SPA-relative canonical product path: {@code /product/<id>-<slug>}, bare when the slug is empty. */
    public static String productPath(int id, String name) {
        String slug = slug(name);
        return slug.isEmpty() ? "/product/" + id : "/product/" + id + "-" + slug;
    }
}
