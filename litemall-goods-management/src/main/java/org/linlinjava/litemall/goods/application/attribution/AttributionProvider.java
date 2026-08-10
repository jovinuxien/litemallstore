package org.linlinjava.litemall.goods.application.attribution;

import org.linlinjava.litemall.db.domain.LitemallCjProduct;

/**
 * Wave-25 attribution seam: turns sourcing context into a brand/store identity that the promote
 * path lands in {@code litemall_brand}, keyed {@code (source, external_id)}, and links via
 * {@code litemall_goods.brand_id}.
 *
 * <p>A FUTURE brand/supplier API plugs in as ONE new provider bean writing the SAME table through
 * the SAME upsert — no schema change, no SPA change. Rules every provider write obeys (enforced by
 * {@code CjProductPromotionService}, the single consumer):
 * <ul>
 *   <li>Provider-created rows land {@code display_enabled=0} — captured and linked, never rendered
 *       until an admin curates (renames) and enables them.</li>
 *   <li>A provider upsert NEVER overwrites an existing row's name — admin renames are permanent.</li>
 *   <li>Providers only (re)link goods whose current attribution is absent or provider-owned;
 *       a {@code source='manual'} admin assignment always wins.</li>
 * </ul>
 */
public interface AttributionProvider {

    /** Source tag of admin-created rows; providers must never use it. */
    String SOURCE_MANUAL = "manual";

    /** Consumer brand — renders as "Brand". */
    byte KIND_BRAND = 0;
    /** Supplier store — renders as "Sold by", never as "Brand". */
    byte KIND_STORE = 1;

    /** Source tag this provider owns (e.g. {@code cj-supplier}); its rows are keyed on it. */
    String source();

    /**
     * Resolve the attribution for one CJ snapshot row (the promote-path context), or null when
     * this provider has nothing to say about it — the honest degradation path.
     */
    Attribution resolve(LitemallCjProduct snapshot);

    /** What a provider knows about a brand/store. {@code logo} is optional (may be null). */
    record Attribution(byte kind, String name, String externalId, String logo) {
    }
}
