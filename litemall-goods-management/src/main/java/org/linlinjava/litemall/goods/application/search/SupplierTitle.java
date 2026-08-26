package org.linlinjava.litemall.goods.application.search;

import java.util.regex.Pattern;

/**
 * Strips supplier bookkeeping that CJ leaks into the customer-facing product title.
 *
 * <p>Currently one rule: a leading {@code "Support Pan European："} logistics note (68 live
 * products on 2026-08-26), which shows on the PDP and in the merchant feed and reads as an
 * unfinished listing. The colon may be ASCII or the CJK fullwidth form (U+FF1A), which is what
 * CJ actually sends.
 *
 * <h2>Why this is applied at BOTH seams</h2>
 * It first shipped only in {@link CatalogHygieneService}, which rewrites {@code litemall_goods}
 * in place. That was a one-shot cleanup of a value the promote path REGENERATES: the adapter
 * writes {@code goods.name} from the CJ snapshot title on every promote, so the very next
 * promote wrote all 68 prefixes straight back — observed in production the same day, when a
 * full promote run for an unrelated price change silently undid the entire cleanup.
 *
 * <p>So the durable rule lives in the anti-corruption adapter, where the customer-facing name is
 * DERIVED, and hygiene keeps it only to repair rows already written. The snapshot itself
 * ({@code litemall_cj_product.title}) is deliberately left untouched — its job is to mirror what
 * CJ sent, and rewriting it would destroy the ability to tell the two apart.
 *
 * <p>The lesson generalises: cleaning a derived column is undone by whatever derives it. Fix it
 * where it is computed, or the fix has a shelf life of one sync.
 */
public final class SupplierTitle {

    /** Leading supplier logistics note; ASCII or fullwidth (U+FF1A) colon, optional. */
    private static final Pattern SUPPLIER_PREFIX = Pattern.compile(
            "^\\s*Support\\s+Pan\\s+European\\s*[:\\uFF1A]?\\s*", Pattern.CASE_INSENSITIVE);

    private SupplierTitle() {
    }

    /**
     * @return the title without the supplier prefix; the input unchanged when no prefix is
     *         present, or null for a null input. A title that is ONLY the prefix is returned
     *         unchanged — never trade a bad title for an empty one; the readability check in
     *         {@link CatalogHygieneService} then routes it to rename-or-retire instead.
     */
    public static String strip(String name) {
        if (name == null) {
            return null;
        }
        String out = SUPPLIER_PREFIX.matcher(name).replaceFirst("").trim();
        return out.isEmpty() ? name : out;
    }
}
