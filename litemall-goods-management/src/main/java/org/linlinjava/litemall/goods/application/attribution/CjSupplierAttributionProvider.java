package org.linlinjava.litemall.goods.application.attribution;

import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.springframework.stereotype.Component;

/**
 * Attribution impl #1: the CJ supplier behind a snapshot row, from the {@code supplier_id} /
 * {@code supplier_name} columns the detail enrichment persists (V60). CJ has no consumer-brand
 * field in any API, so everything it yields is a KIND_STORE ("Sold by") attribution.
 *
 * <p>The name is the RAW CJ legal-entity string (e.g. "Wenling Chengdong Jiuwei Shoe and Hat
 * Business") — usable as a stable identity, unfit for customers; the display_enabled gate keeps it
 * hidden until an admin renames it. Rows without a captured supplier resolve to null (no fake
 * attribution).
 */
@Component
public class CjSupplierAttributionProvider implements AttributionProvider {

    public static final String SOURCE = "cj-supplier";

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public Attribution resolve(LitemallCjProduct snapshot) {
        // Wave 25.1 junk gate: CJ delivers literal "{}" in these fields on some products, and this
        // is the authoritative choke point — it also neutralizes junk ALREADY persisted on
        // litemall_cj_product rows (the enrich statement's COALESCE never clears them). A junk
        // supplierId means no attribution at all; a junk supplierName with a real id falls back to
        // the id as the (display_enabled=0, admin-renamed) placeholder name.
        if (snapshot == null || !AttributionProvider.usableIdentityField(snapshot.getSupplierId())) {
            return null;
        }
        String name = AttributionProvider.usableIdentityField(snapshot.getSupplierName())
                ? snapshot.getSupplierName().trim()
                : snapshot.getSupplierId().trim();
        return new Attribution(KIND_STORE, name, snapshot.getSupplierId().trim(), null);
    }
}
