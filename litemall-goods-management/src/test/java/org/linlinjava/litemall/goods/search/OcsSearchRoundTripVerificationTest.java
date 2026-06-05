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
        // These six Result fields are populated for every on-sale goods, so they are
        // always present in result data. brand is also a Result field but OCS omits it
        // from data when the goods has no brand (brand_id 0 -> null), and category_ids
        // is Facet-usage only (never in result data) — both are asserted below.
        assertThat(data).containsKeys(
                "title", "price", "discount_price", "description",
                "image_url", "category_names");

        // brand (Search+Result+Facet) and category_ids (Facet-only) must still be
        // indexed — assert both surface as search facets.
        List<Map<String, Object>> facets = asList(slices.get(0).get("facets"));
        assertThat(facets).as("facets present").isNotNull();
        assertThat(facetFieldNames(facets))
                .as("brand and category_ids indexed as facets")
                .contains("brand", "category_ids");

        // Prove brand also round-trips as a result field: pull a real brand value from
        // the brand facet, search it (brand is a Search field), and assert the matching
        // document carries brand in its result data — the ninth field. Self-adapting to
        // the live data so it never hardcodes a brand name.
        String brandTerm = firstBrandSearchTerm(facets);
        assumeTrue(brandTerm != null, "no branded goods indexed — skipping brand result-data check");
        Map<String, Object> brandResult = trySearch(brandTerm);
        List<Map<String, Object>> brandSlices = brandResult == null ? null : asList(brandResult.get("slices"));
        assumeTrue(brandSlices != null && !brandSlices.isEmpty(), "brand search returned nothing — skipping");
        List<Map<String, Object>> brandHits = asList(brandSlices.get(0).get("hits"));
        assertThat(brandHits).as("brand search hits").isNotEmpty();
        Map<String, Object> brandData = asMap(asMap(brandHits.get(0).get("document")).get("data"));
        assertThat(brandData).as("brand present in result data for a branded goods").containsKey("brand");
    }

    /** Field names of the slice's facets (e.g. brand, price, category_ids, category_names). */
    private static List<String> facetFieldNames(List<Map<String, Object>> facets) {
        return facets.stream().map(f -> (String) f.get("fieldName")).toList();
    }

    /** First whitespace-delimited token of the first brand facet entry's key, or null. */
    @SuppressWarnings("unchecked")
    private static String firstBrandSearchTerm(List<Map<String, Object>> facets) {
        for (Map<String, Object> facet : facets) {
            if (!"brand".equals(facet.get("fieldName"))) {
                continue;
            }
            List<Map<String, Object>> entries = (List<Map<String, Object>>) facet.get("entries");
            if (entries == null || entries.isEmpty()) {
                return null;
            }
            String key = (String) entries.get(0).get("key");
            if (key == null || key.isBlank()) {
                return null;
            }
            return key.trim().split("\\s+")[0];
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
        try {
            return http.getForObject(SEARCH_URL + "/search-api/v1/search/{index}?q={q}&offset=0&limit=5",
                    Map.class, INDEX, q);
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
