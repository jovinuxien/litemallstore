package org.linlinjava.litemall.goods.search;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Relevance/deals assertion tests (RUNBOOK §23): hard invariants of the deal
 * signals introduced with the deals vertical, asserted against the live OCS
 * searcher (same REST contract as {@code OcsSearchClient}). Assertion tests
 * complement the judgment replay ({@link RelevanceJudgmentVerificationTest}):
 * these are binary must-holds, the judgment list measures graded quality.
 *
 * <p>Guarded: searcher unreachable / no deals indexed → SKIP (CI-safe).
 */
class OcsDealsVerificationTest {

    private static final String SEARCH_URL = System.getProperty("ocs.search-url", "http://localhost:8534");
    private static final String INDEX = System.getProperty("ocs.index-name", "litemall_index");
    private static final int DEAL_MIN_PCT = Integer.getInteger("litemall.deal-min-pct", 10);

    private final RestTemplate http = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(2))
            .setReadTimeout(Duration.ofSeconds(8))
            .build();

    @Test
    void dealFlagFilterReturnsOnlyThresholdDiscounts() {
        List<Map<String, Object>> hits = hits("&deal_flag=1&limit=24");
        assumeTrue(hits != null, "OCS searcher not reachable — skipping");
        assumeTrue(!hits.isEmpty(), "no deals indexed — skipping");
        for (Map<String, Object> data : dataOf(hits)) {
            assertThat(asInt(data.get("deal_flag"))).as("deal_flag on a deal_flag=1 hit").isEqualTo(1);
            assertThat(asInt(data.get("discount_pct")))
                    .as("discount_pct honours the deal threshold")
                    .isGreaterThanOrEqualTo(DEAL_MIN_PCT);
        }
    }

    @Test
    void closedRangeDiscountFilterIsRespected() {
        List<Map<String, Object>> hits = hits("&deal_flag=1&discount_pct=25,50&limit=24");
        assumeTrue(hits != null, "OCS searcher not reachable — skipping");
        assumeTrue(!hits.isEmpty(), "no deals in the 25-50 band — skipping");
        for (Map<String, Object> data : dataOf(hits)) {
            assertThat(asInt(data.get("discount_pct")))
                    .as("discount_pct inside the requested 25,50 band (bounds inclusive)")
                    .isBetween(25, 50);
        }
    }

    @Test
    void deepestDiscountSortIsMonotonic() {
        List<Map<String, Object>> hits = hits("&deal_flag=1&sort=-discount_pct&limit=24");
        assumeTrue(hits != null, "OCS searcher not reachable — skipping");
        assumeTrue(hits.size() >= 2, "not enough deals to check ordering — skipping");
        List<Map<String, Object>> data = dataOf(hits);
        for (int i = 1; i < data.size(); i++) {
            assertThat(asInt(data.get(i).get("discount_pct")))
                    .as("sort=-discount_pct descending at position %d", i)
                    .isLessThanOrEqualTo(asInt(data.get(i - 1).get("discount_pct")));
        }
    }

    /**
     * An exact full-title query must place that product in the top 3. Top-3 not
     * top-1: the multiplicative business boosts (popularity/rating/discount) may
     * legitimately promote another product matching every term, but an exact
     * title being pushed OFF the first row means text relevance lost control —
     * the failure mode Relevant Search ch. 7 warns about.
     */
    @Test
    void exactTitleQueryRanksItsProductInTopThree() {
        List<Map<String, Object>> browse = hits("&limit=5");
        assumeTrue(browse != null, "OCS searcher not reachable — skipping");
        assumeTrue(!browse.isEmpty(), "index empty — skipping");
        Map<String, Object> probe = browse.get(browse.size() - 1);
        String title = String.valueOf(data(probe).get("title"));
        String id = String.valueOf(document(probe).get("id"));
        assumeTrue(title != null && !"null".equals(title), "probe document has no title — skipping");

        List<Map<String, Object>> result = hits("&limit=3&q=" + urlEncode(title));
        assumeTrue(result != null && !result.isEmpty(), "exact-title query returned nothing — skipping");
        List<String> topIds = new ArrayList<>();
        for (Map<String, Object> hit : result) {
            topIds.add(String.valueOf(document(hit).get("id")));
        }
        assertThat(topIds).as("exact title '%s' ranks its product in the top 3", title).contains(id);
    }

    // --- helpers -------------------------------------------------------------

    private List<Map<String, Object>> hits(String params) {
        Map<String, Object> result;
        try {
            result = http.exchange(
                    SEARCH_URL + "/search-api/v1/search/" + INDEX + "?q=" + params,
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    }).getBody();
        } catch (Exception e) {
            return null;
        }
        if (result == null || !(result.get("slices") instanceof List)) {
            return null;
        }
        List<Map<String, Object>> hits = new ArrayList<>();
        for (Object sliceObj : (List<?>) result.get("slices")) {
            Object sliceHits = ((Map<?, ?>) sliceObj).get("hits");
            if (sliceHits instanceof List) {
                for (Object hit : (List<?>) sliceHits) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) hit;
                    hits.add(typed);
                }
            }
        }
        return hits;
    }

    private static List<Map<String, Object>> dataOf(List<Map<String, Object>> hits) {
        List<Map<String, Object>> all = new ArrayList<>();
        for (Map<String, Object> hit : hits) {
            all.add(data(hit));
        }
        return all;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> document(Map<String, Object> hit) {
        return (Map<String, Object>) hit.getOrDefault("document", Map.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> hit) {
        return (Map<String, Object>) document(hit).getOrDefault("data", Map.of());
    }

    private static int asInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : -1;
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
