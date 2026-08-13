package org.linlinjava.litemall.goods.infrastructure.configuration;

import java.math.BigDecimal;

/**
 * Wave 26 Phase 2 deliverable 4: sourcing filters for CJ {@code /product/list}. These parameters
 * have always existed upstream; we simply never sent them (the client passed only pageNum, pageSize
 * and categoryId), so the catalogue was assembled by mirroring whatever CJ happened to return.
 *
 * <p><b>Why acquisition, not filtering.</b> Phase 1a measured CJ's EU warehouse footprint at 0.30%
 * of supply — filtering our existing ~15k catalogue down to EU-held stock would leave roughly 40
 * products. EU-stocked SKUs therefore have to be ACQUIRED at the sourcing seam, which is here.
 *
 * <p><b>Measured CJ constraints (2026-08-13, live) — enforced locally so a misconfiguration fails
 * at boot with a clear message rather than as an opaque upstream 400:</b>
 * <ul>
 *   <li>{@code countryCode} takes ONE code, max 4 characters. A comma-separated list is rejected
 *       upstream with "countryCode only within 4 characters", so there is no way to express
 *       "any EU warehouse" in one call. Germany is the only EU warehouse CJ actually stocks
 *       (ES/CZ/IT/NL/BE/PL/SE/DK/AT and "EU" all return zero).</li>
 *   <li>{@code minPrice}/{@code maxPrice} bound CJ's <b>cost</b>, NOT our retail. At margin 2.5 a
 *       €25–80 retail band corresponds to roughly €10–32 of cost — getting this backwards would
 *       source entirely the wrong products.</li>
 * </ul>
 *
 * <p>{@link #NONE} is the default everywhere: an absent filter must produce requests byte-identical
 * to today's, so enabling this is opt-in per catalog target and changes nothing until configured.
 */
public record CjListFilter(String countryCode, BigDecimal minPrice, BigDecimal maxPrice) {

    /** CJ's hard limit on the countryCode parameter, measured live. */
    public static final int COUNTRY_CODE_MAX = 4;

    /** No filtering — requests are byte-identical to the pre-Wave-26 client. */
    public static final CjListFilter NONE = new CjListFilter(null, null, null);

    public CjListFilter {
        countryCode = normalizeCountryCode(countryCode);
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException(
                    "CJ list filter: minPrice " + minPrice + " exceeds maxPrice " + maxPrice);
        }
    }

    private static String normalizeCountryCode(String raw) {
        if (raw == null) {
            return null;
        }
        String c = raw.trim();
        if (c.isEmpty()) {
            return null;
        }
        if (c.indexOf(',') >= 0) {
            throw new IllegalArgumentException(
                    "CJ list filter: countryCode takes ONE code, not a list ('" + c + "'). CJ rejects "
                            + "comma-separated values, and per-country results cannot be summed "
                            + "because a product stocked in two countries appears in both.");
        }
        if (c.length() > COUNTRY_CODE_MAX) {
            throw new IllegalArgumentException(
                    "CJ list filter: countryCode '" + c + "' exceeds CJ's " + COUNTRY_CODE_MAX
                            + "-character limit.");
        }
        return c;
    }

    /** True when nothing is set — the caller then takes the untouched legacy request path. */
    public boolean isEmpty() {
        return countryCode == null && minPrice == null && maxPrice == null;
    }

    /**
     * Stable fragment identifying this filter in the raw-page cache key. Without it a filtered fetch
     * would happily reuse pages cached by an UNFILTERED run of the same category — silently
     * returning the wrong product set and looking like the filter did nothing.
     */
    public String cacheSuffix() {
        if (isEmpty()) {
            return "";
        }
        return ":f(" + (countryCode == null ? "" : countryCode)
                + "/" + (minPrice == null ? "" : minPrice.toPlainString())
                + "/" + (maxPrice == null ? "" : maxPrice.toPlainString()) + ")";
    }
}
