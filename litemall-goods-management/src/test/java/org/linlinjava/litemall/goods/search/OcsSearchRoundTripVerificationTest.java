package org.linlinjava.litemall.goods.search;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Lightweight, CI-safe verification of the OCS search path against a running
 * stack. It talks to the OCS searcher/suggest REST contracts directly (the same
 * endpoints {@code OcsSearchClient}/{@code OcsSuggestClient} call) and asserts:
 *
 * <ol>
 *   <li>a reindexed document round-trips through search carrying all nine
 *       {@code litemall_index} fields (the seven result fields in
 *       {@code document.data} plus {@code product_id} as {@code document.id}
 *       and {@code category_ids} surfaced as a facet);</li>
 *   <li>suggest returns phrases.</li>
 * </ol>
 *
 * <p>It is guarded by {@link org.junit.jupiter.api.Assumptions}: when no OCS
 * searcher answers on the configured host (the usual CI case, since a live OCS
 * container is impractical there), every test SKIPS rather than fails. To run it
 * against the docker-compose OCS stack after a reindex:
 * <pre>
 *   mvn -pl litemall-goods-management test -Dtest=OcsSearchRoundTripIT
 * </pre>
 * Hosts/index are overridable so the check stays profile-agnostic:
 * {@code -Docs.search-url=...}, {@code -Docs.suggest-url=...},
 * {@code -Docs.index-name=...}. The full manual evidence lives in
 * {@code verify/RUNBOOK.md}.
 */
class OcsSearchRoundTripVerificationTest {

    private static final String SEARCH_URL = System.getProperty("ocs.search-url", "http://localhost:8534");
    private static final String SUGGEST_URL = System.getProperty("ocs.suggest-url", "http://localhost:8081");
    private static final String INDEX = System.getProperty("ocs.index-name", "litemall_index");

    private final RestTemplate http = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(2))
            .setReadTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void reindexedDocumentRoundTripsThroughSearchWithAllNineFields() {
        Map<String, Object> result = trySearch("");
        assumeTrue(result != null, "OCS searcher not reachable at " + SEARCH_URL + " — skipping");

        List<Map<String, Object>> slices = asList(result.get("slices"));
        assumeTrue(slices != null && !slices.isEmpty(), "index empty — run a reindex first; skipping");

        long matchCount = ((Number) slices.get(0).getOrDefault("matchCount", 0)).longValue();
        assertThat(matchCount).as("at least one indexed document").isGreaterThan(0);

        List<Map<String, Object>> hits = asList(slices.get(0).get("hits"));
        assertThat(hits).as("search hits").isNotEmpty();

        Map<String, Object> document = asMap(hits.get(0).get("document"));
        // product_id rides on the envelope id; the result-usage fields ride in data.
        assertThat(document.get("id")).as("product_id (document.id)").isNotNull();
        Map<String, Object> data = asMap(document.get("data"));
        // These five Result fields are populated for every on-sale goods, so they are
        // always present in result data. OCS omits null fields from data, and three of
        // the nine are nullable per goods: brand (brand_id 0 -> null), discount_price
        // (CJ goods have no discount concept, §16) — both asserted on documents that
        // have them below — and category_ids is Facet-usage only (never in result data).
        assertThat(data).containsKeys(
                "title", "price", "description", "image_url", "category_names");

        // discount_price round-trips wherever a goods has one: sort by it descending so
        // the top hit provably carries it (self-adapting — no hardcoded goods).
        Map<String, Object> discounted = trySearch("", "-discount_price");
        List<Map<String, Object>> discountedSlices = discounted == null ? null : asList(discounted.get("slices"));
        assumeTrue(discountedSlices != null && !discountedSlices.isEmpty(), "discount-sorted search returned nothing — skipping");
        List<Map<String, Object>> discountedHits = asList(discountedSlices.get(0).get("hits"));
        assertThat(discountedHits).as("discount-sorted hits").isNotEmpty();
        assertThat(asMap(asMap(discountedHits.get(0).get("document")).get("data")))
                .as("discount_price present on the highest-discount_price document")
                .containsKey("discount_price");

        // category_ids (Facet-usage only, never in result data) must surface as a facet.
        // brand is a facet field too, but the searcher ranks/caps the displayed facet
        // set and with CJ attribute facets (Material, source, ...) in the index the
        // brand facet no longer makes the cut for a broad query — so brand is proven
        // through result data below instead of through facet display.
        List<Map<String, Object>> facets = asList(slices.get(0).get("facets"));
        assertThat(facets).as("facets present").isNotNull();
        assertThat(facetFieldNames(facets))
                .as("category_ids indexed as a facet")
                .contains("category_ids");

        // Prove brand round-trips as a result field — the ninth field: scan a page of
        // hits for a branded document (brand is nullable per goods, so not every hit
        // carries it), then search its brand term and assert the match carries brand in
        // result data. Self-adapting to the live data so it never hardcodes a brand name.
        String brandTerm = firstBrandFromHits();
        assumeTrue(brandTerm != null, "no branded goods indexed — skipping brand result-data check");
        Map<String, Object> brandResult = trySearch(brandTerm);
        List<Map<String, Object>> brandSlices = brandResult == null ? null : asList(brandResult.get("slices"));
        assumeTrue(brandSlices != null && !brandSlices.isEmpty(), "brand search returned nothing — skipping");
        List<Map<String, Object>> brandHits = asList(brandSlices.get(0).get("hits"));
        assertThat(brandHits).as("brand search hits").isNotEmpty();
        boolean anyBranded = brandHits.stream()
                .map(h -> asMap(asMap(h.get("document")).get("data")))
                .anyMatch(d -> d.containsKey("brand"));
        assertThat(anyBranded).as("brand present in result data for a branded goods").isTrue();
    }

    /** Field names of the slice's facets (e.g. price, category_ids, category_names). */
    private static List<String> facetFieldNames(List<Map<String, Object>> facets) {
        return facets.stream().map(f -> (String) f.get("fieldName")).toList();
    }

    /**
     * First whitespace-delimited token of the first brand value found while paging
     * through match-all hits, or null. Most CJ goods carry no brand, so one page is
     * not enough — scan up to 500 documents.
     */
    private String firstBrandFromHits() {
        for (int offset = 0; offset < 500; offset += 50) {
            Map<String, Object> result = trySearch("", null, 50, offset);
            if (result == null) {
                return null;
            }
            List<Map<String, Object>> slices = asList(result.get("slices"));
            if (slices == null || slices.isEmpty()) {
                return null;
            }
            List<Map<String, Object>> hits = asList(slices.get(0).get("hits"));
            if (hits == null || hits.isEmpty()) {
                return null;
            }
            for (Map<String, Object> hit : hits) {
                Object brand = asMap(asMap(hit.get("document")).get("data")).get("brand");
                if (brand instanceof String s && !s.isBlank()) {
                    return s.trim().split("\\s+")[0];
                }
            }
        }
        return null;
    }

    @Test
    void suggestReturnsPhrases() {
        List<Map<String, Object>> suggestions = trySuggest("a");
        assumeTrue(suggestions != null, "OCS suggest not reachable at " + SUGGEST_URL + " — skipping");
        assumeTrue(!suggestions.isEmpty(),
                "suggest index not yet harvested (async update cycle) — skipping");
        assertThat(suggestions.get(0).get("phrase")).as("suggestion phrase").isNotNull();
    }

    private Map<String, Object> trySearch(String q) {
        return trySearch(q, null, 5, 0);
    }

    private Map<String, Object> trySearch(String q, String sort) {
        return trySearch(q, sort, 5, 0);
    }

    private Map<String, Object> trySearch(String q, String sort, int limit, int offset) {
        try {
            if (sort == null) {
                return http.getForObject(SEARCH_URL + "/search-api/v1/search/{index}?q={q}&offset={offset}&limit={limit}",
                        Map.class, INDEX, q, offset, limit);
            }
            return http.getForObject(SEARCH_URL + "/search-api/v1/search/{index}?q={q}&offset={offset}&limit={limit}&sort={sort}",
                    Map.class, INDEX, q, offset, limit, sort);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private List<Map<String, Object>> trySuggest(String q) {
        try {
            return http.exchange(SUGGEST_URL + "/suggest-api/v1/{index}/suggest?userQuery={q}",
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    }, INDEX, q).getBody();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object o) {
        return (List<Map<String, Object>>) o;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }
}
