package org.linlinjava.litemall.goods.application.seo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Outbound seam for search-demand facts about a catalogue term — what shoppers actually type,
 * and how often — used to write product titles and briefs against real demand instead of the
 * supplier's wording.
 *
 * <p>This interface is deliberately stated in THIS service's vocabulary. The only implementation
 * today reads the seo_plateform research API, which in turn buys from DataForSEO, but neither
 * appears here: no location code, no language code, no provider envelope, no tenant. An
 * implementation reading a different provider — or a spreadsheet an analyst maintains — plugs in
 * as one bean with no change above this line. Everything provider-shaped lives behind the ACL in
 * {@code infrastructure/acl/seo}.
 *
 * <p><strong>Best-effort by contract.</strong> Every call is fail-soft in exactly the sense
 * {@code CrawledMarketDataProvider} established: research disabled, the platform unreachable, the
 * spend cap reached, or an unexpected envelope all yield an EMPTY list, and the caller proceeds
 * without demand signal. Keyword data enriches catalogue copy; it must never be able to fail a
 * product page, a feed export, or an admin save.
 *
 * <p><strong>Cost is real.</strong> The backing provider is pay-as-you-go and bills per seed, not
 * per keyword returned — a malformed request is charged for like a good one. Call this per
 * CATEGORY, never per product: one seed answers for every goods in the category, and the platform
 * caches a bought answer for 14 days. Fanning this out across a 3,700-product catalogue would buy
 * the same head terms thousands of times.
 */
public interface KeywordResearchProvider {

    /**
     * Search-demand facts for one seed term, best first, or an empty list when unavailable.
     *
     * @param seed a catalogue term to research — a category name, not a product name
     * @param limit maximum rows to return; the provider may return fewer
     */
    List<KeywordDemand> demandFor(String seed, int limit);

    /**
     * One term shoppers search for, with what is known about demand for it.
     *
     * <p>Every metric is nullable and nullable means UNKNOWN, never zero. A term the provider
     * returned but could not price is a term with unknown competition — writing 0 there would
     * make it look like the cheapest keyword in the set, which is precisely backwards.
     *
     * @param term         the search phrase itself
     * @param monthlySearches average monthly searches, or null when unknown
     * @param competition  0..1 paid-competition index, or null when unknown
     * @param difficulty   0..100 organic difficulty, or null when unknown
     */
    record KeywordDemand(String term, Integer monthlySearches, BigDecimal competition,
                         Integer difficulty) {
    }
}
