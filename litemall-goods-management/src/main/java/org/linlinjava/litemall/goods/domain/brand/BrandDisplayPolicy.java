package org.linlinjava.litemall.goods.domain.brand;

import org.linlinjava.litemall.db.domain.LitemallBrand;

/**
 * The Wave-25 curation gate, in one place.
 *
 * <p>{@code litemall_brand} holds rows from two very different origins. Manual rows are consumer
 * brands an admin typed ({@code kind=0}). Provider rows are created by the CJ attribution seam
 * from {@code supplierName} ({@code kind=1}) and land {@code display_enabled=0} on purpose:
 * their names are raw legal entities — "Yiwu Ruijia Auto Supplies Co., Ltd.", "Shenzhen Kagu
 * Technology Co., Ltd." — which are honest bookkeeping and terrible storefront copy. An admin
 * renames one to something customer-worthy and enables it; only then may it be shown.
 *
 * <h2>Why this is a class and not three {@code if}s</h2>
 * The gate was originally written inline at each consuming site — the public brand read, the PDP
 * payload, the merchant feed. Three copies of a rule is a rule that one more site can forget, and
 * one did: {@code LitemallProductIndexingService} emitted {@code doc.setBrand(brand.getName())}
 * with no check at all, so every raw supplier legal entity flowed into the OCS {@code brand}
 * facet and — because the suggest index sources {@code brand} — into search autocomplete too.
 * Measured live on trovemo.com 2026-08-26: the facet on {@code q=lamp} offered "Yiwu Ruijia Auto
 * Supplies Co., Ltd." and "Sichuan Micro-entrepreneur E-commerce Co., Ltd." as BRANDS to filter
 * by, and {@code q=co., ltd} autocompleted eight supplier names.
 *
 * <p>The lesson generalises: a policy enforced by convention at N call sites is enforced at N-1
 * of them the moment someone adds the Nth. It belongs in one testable place that every site
 * calls.
 *
 * <h2>The two predicates</h2>
 * They are deliberately distinct, because the sites genuinely differ:
 * <ul>
 *   <li>{@link #isDisplayable} — may this row be shown to a customer AT ALL? Used by the public
 *       brand read and the PDP, which render {@code kind} as a "Brand" vs "Sold by" label and so
 *       can carry a supplier store honestly.</li>
 *   <li>{@link #isConsumerBrand} — may this row be presented AS A BRAND, unlabelled? Used by the
 *       merchant feed's {@code brand} column and by the search index's {@code brand} field. Both
 *       are contexts where the value appears with no room for a "Sold by" qualifier: the SPA
 *       labels the facet "Brand", and Merchant Center reads the column as a brand claim. Putting
 *       a supplier in either repeats the exact mislabel Wave 25 was built to prevent.</li>
 * </ul>
 *
 * <p>Both are null-safe and default to CLOSED: an absent row, an absent {@code display_enabled}
 * and an absent {@code kind} all deny. Attribution is decoration — the honest degradation is no
 * brand at all, never a guess.
 */
public final class BrandDisplayPolicy {

    private BrandDisplayPolicy() {
    }

    /**
     * Whether the brand may be surfaced to customers in a context that labels what it is.
     *
     * @return true only for a live, non-deleted, admin-enabled row; false for null.
     */
    public static boolean isDisplayable(LitemallBrand brand) {
        return brand != null
                && !Boolean.TRUE.equals(brand.getDeleted())
                && Boolean.TRUE.equals(brand.getDisplayEnabled());
    }

    /**
     * Whether the brand may be presented as a bare brand claim, with no "Sold by" qualifier.
     *
     * @return true only for a displayable row that is a consumer brand ({@code kind == 0});
     *         false for supplier stores, for an unset {@code kind}, and for null.
     */
    public static boolean isConsumerBrand(LitemallBrand brand) {
        return isDisplayable(brand)
                && brand.getKind() != null
                && brand.getKind() == 0;
    }
}
