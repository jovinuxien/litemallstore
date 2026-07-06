package org.linlinjava.litemall.goods.search;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Lightweight, CI-safe verification of the OCS INCREMENTAL indexing contract
 * against a running stack — the wire shape {@code OcsProductIndexer.upsert}/
 * {@code delete} use when {@code GoodsIndexEvent} → {@code MessageConsumer}
 * fires on a goods create/update/delete:
 *
 * <ol>
 *   <li>{@code PUT /indexer-api/v1/update/<index>} with one
 *       {@code {"id": ..., "data": {nine fields}}} document makes it
 *       searchable WITHOUT a full reindex;</li>
 *   <li>{@code DELETE /indexer-api/v1/update/<index>?id=...} removes exactly
 *       that document again.</li>
 * </ol>
 *
 * <p>The full application-level loop (admin goods write → RabbitMQ
 * {@code goods.index.queue} → {@code MessageConsumer} → these endpoints) is
 * proven live and recorded in {@code verify/RUNBOOK.md} §17; this check pins
 * the REST contract those components depend on.
 *
 * <p>Guarded by {@link org.junit.jupiter.api.Assumptions}: SKIPS when no OCS
 * indexer/searcher answers (the usual CI case). Hosts/index overridable:
 * {@code -Docs.indexer-url}, {@code -Docs.search-url}, {@code -Docs.index-name}.
 * The synthetic document uses an id far outside the goods id range and is
 * deleted again in a {@code finally} block, so the shared index stays clean
 * even on assertion failure.
 */
class OcsIncrementalIndexVerificationTest {

    private static final String INDEXER_URL = System.getProperty("ocs.indexer-url", "http://localhost:8535");
    private static final String SEARCH_URL = System.getProperty("ocs.search-url", "http://localhost:8534");
    private static final String INDEX = System.getProperty("ocs.index-name", "litemall_index");

    private static final String PROBE_ID = "990000901";
    private static final String PROBE_TOKEN = "zzincrementalprobequilt";

    private final RestTemplate http = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(2))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();

    @Test
    void incrementalUpsertBecomesSearchableAndDeleteRemovesIt() throws Exception {
        assumeTrue(searcherUp(), "OCS searcher not reachable at " + SEARCH_URL + " — skipping");
        assumeTrue(indexerUp(), "OCS indexer not reachable at " + INDEXER_URL + " — skipping");

        try {
            upsertProbeDocument();
            assertThat(awaitHitCount(1)).as("incrementally upserted document searchable").isEqualTo(1);

            Map<String, Object> data = topHitData();
            assertThat(data).containsKeys(
                    "title", "price", "discount_price", "description",
                    "image_url", "brand", "category_names");
        } finally {
            deleteProbeDocument();
        }
        assertThat(awaitHitCount(0)).as("document gone after incremental delete").isEqualTo(0);
    }

    private void upsertProbeDocument() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("product_id", PROBE_ID);
        fields.put("title", "Incremental probe " + PROBE_TOKEN);
        fields.put("price", 99.0);
        fields.put("discount_price", 79.0);
        fields.put("description", "synthetic incremental-contract probe document");
        fields.put("image_url", "http://localhost/probe.png");
        fields.put("brand", "ProbeBrand");
        fields.put("category_names", List.of("probe root", "probe leaf"));
        fields.put("category_ids", List.of("9990001", "9990002"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        http.exchange(INDEXER_URL + "/indexer-api/v1/update/{index}",
                HttpMethod.PUT,
                new HttpEntity<>(List.of(Map.of("id", PROBE_ID, "data", fields)), headers),
                Void.class, INDEX);
    }

    private void deleteProbeDocument() {
        try {
            http.delete(INDEXER_URL + "/indexer-api/v1/update/{index}?id={id}", INDEX, PROBE_ID);
        } catch (RuntimeException ignored) {
            // best-effort cleanup; the assertion on awaitHitCount(0) reports real failures
        }
    }

    /** Poll search (ES refresh is asynchronous, ~1s) until the probe hit count matches. */
    private long awaitHitCount(long expected) throws InterruptedException {
        long count = -1;
        for (int i = 0; i < 20; i++) {
            count = probeHitCount();
            if (count == expected) {
                return count;
            }
            Thread.sleep(500);
        }
        return count;
    }

    private long probeHitCount() {
        Map<String, Object> result = trySearch(PROBE_TOKEN);
        if (result == null) {
            return -1;
        }
        List<Map<String, Object>> slices = asList(result.get("slices"));
        if (slices == null || slices.isEmpty()) {
            return 0;
        }
        return ((Number) slices.get(0).getOrDefault("matchCount", 0)).longValue();
    }

    private Map<String, Object> topHitData() {
        Map<String, Object> result = trySearch(PROBE_TOKEN);
        List<Map<String, Object>> hits = asList(asList(result.get("slices")).get(0).get("hits"));
        Map<String, Object> document = asMap(hits.get(0).get("document"));
        assertThat(document.get("id")).as("product_id (document.id)").isEqualTo(PROBE_ID);
        return asMap(document.get("data"));
    }

    private boolean searcherUp() {
        return trySearch("") != null;
    }

    private boolean indexerUp() {
        try {
            // Any HTTP status (even 404 on the root path) proves the indexer answers;
            // only a connect/timeout failure means it is down. Deliberately NOT
            // full/start — that would open a real import session as a side effect.
            http.getForObject(INDEXER_URL + "/", String.class);
            return true;
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Map<String, Object> trySearch(String q) {
        try {
            return http.getForObject(SEARCH_URL + "/search-api/v1/search/{index}?q={q}&offset=0&limit=5",
                    Map.class, INDEX, q);
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
