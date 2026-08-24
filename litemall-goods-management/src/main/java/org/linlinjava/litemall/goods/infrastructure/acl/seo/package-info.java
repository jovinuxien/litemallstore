/**
 * Anti-corruption layer for the seo_plateform research API.
 *
 * <p><strong>Boundary rule:</strong> nothing in {@code domain.**} — and nothing in
 * {@code application.**} beyond the port itself — may import from this package. Callers talk to
 * the {@link org.linlinjava.litemall.goods.application.seo.KeywordResearchProvider} port; the
 * implementation here is the only place that knows the platform exists.
 *
 * <p>Three foreign models stack up behind this seam and NONE of them may cross it: DataForSEO's
 * request/response envelope, the platform's {@code SeoKeywordMetric} row (tenant id, seed keyword,
 * location code, cost in USD, fetch timestamp), and Keycloak's {@code client_credentials} grant.
 * The port speaks only of a term and its demand. That gap is the point — a catalogue copywriter
 * has no business knowing which tenant paid for a row, and a change of provider should not reach
 * past this package.
 *
 * <p>Two properties of the far side that shape the code here and are easy to get wrong:
 * <ul>
 *   <li><strong>The tenant claim is load-bearing.</strong> The research service keys its cache,
 *       cost ledger and monthly spend cap on the tenant in the token. A token missing the claim is
 *       answered with an EMPTY result rather than a 401, so a misconfigured client looks exactly
 *       like a category with no keywords.</li>
 *   <li><strong>Calls cost money and bill per seed.</strong> A failed or malformed request is
 *       charged for like a good one, and the platform caches a bought answer for 14 days. Research
 *       a category, never a product.</li>
 * </ul>
 *
 * <p>If you find yourself wanting to import a platform or provider type from outside this package,
 * the right answer is to grow the port — not to bend the boundary.
 */
package org.linlinjava.litemall.goods.infrastructure.acl.seo;
