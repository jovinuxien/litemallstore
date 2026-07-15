package org.linlinjava.litemall.goods.search;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test-driven relevance (RUNBOOK §23, Relevant Search §9.4/§10.6): replays the
 * graded judgment list in {@code verify/relevance/judgments.csv} against the
 * live OCS searcher and computes nDCG@10 per query. The mean is compared to the
 * committed baseline ({@code verify/relevance/baseline.properties}) with a
 * tolerance — run this after EVERY scoring-configuration or query-configuration
 * change; a drop past tolerance means the change bought its improvement by
 * damaging graded queries.
 *
 * <p>Unjudged documents contribute zero gain (standard practice): a new doc
 * displacing a graded one must earn its place through a regrade, not silently.
 *
 * <p>Guarded like the other OCS verification tests: searcher unreachable or
 * judgments missing → SKIP. When the baseline file is absent the test prints
 * the computed mean (bootstrap mode) instead of asserting — commit it as the
 * new baseline via {@code verify/relevance/baseline.properties}.
 */
class RelevanceJudgmentVerificationTest {

    private static final String SEARCH_URL = System.getProperty("ocs.search-url", "http://localhost:8534");
    private static final String INDEX = System.getProperty("ocs.index-name", "litemall_index");
    /** Mean-nDCG regression tolerance — small re-ranking jitter passes, real damage fails. */
    private static final double TOLERANCE = 0.05;
    private static final int K = 10;

    private final RestTemplate http = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(2))
            .setReadTimeout(Duration.ofSeconds(8))
            .build();

    @Test
    void meanNdcgAtTenDoesNotRegressPastTolerance() throws IOException {
        Path csv = locate("verify/relevance/judgments.csv");
        assumeTrue(csv != null, "judgments.csv not found — skipping");
        Map<String, Map<String, Integer>> judgments = parseJudgments(csv);
        assumeTrue(!judgments.isEmpty(), "judgment list empty — skipping");
        assumeTrue(reachable(), "OCS searcher not reachable at " + SEARCH_URL + " — skipping");

        Map<String, Double> perQuery = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : judgments.entrySet()) {
            List<String> rankedIds = searchTopIds(entry.getKey());
            perQuery.put(entry.getKey(), ndcg(rankedIds, entry.getValue()));
        }
        double mean = perQuery.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);

        StringBuilder report = new StringBuilder(String.format(Locale.ROOT,
                "%nRelevance judgment replay (nDCG@%d, index=%s)%n", K, INDEX));
        perQuery.forEach((q, v) -> report.append(String.format(Locale.ROOT, "  %-24s %.4f%n", q, v)));
        report.append(String.format(Locale.ROOT, "  %-24s %.4f%n", "MEAN", mean));
        System.out.print(report);

        Path baselinePath = locate("verify/relevance/baseline.properties");
        if (baselinePath == null) {
            System.out.printf(Locale.ROOT,
                    "No baseline committed — bootstrap mode. To pin: echo 'mean-ndcg=%.4f' > verify/relevance/baseline.properties%n",
                    mean);
            return;
        }
        Properties baseline = new Properties();
        baseline.load(Files.newBufferedReader(baselinePath, StandardCharsets.UTF_8));
        double pinned = Double.parseDouble(baseline.getProperty("mean-ndcg", "0"));
        assertThat(mean)
                .as("mean nDCG@%d vs baseline %.4f (tolerance %.2f) — a scoring change regressed graded queries", K, pinned, TOLERANCE)
                .isGreaterThanOrEqualTo(pinned - TOLERANCE);
    }

    // --- nDCG ---------------------------------------------------------------

    /** Graded gain 2^g−1, log2 position discount; ideal DCG from the judgment grades sorted desc. */
    private static double ndcg(List<String> rankedIds, Map<String, Integer> grades) {
        double dcg = 0;
        for (int i = 0; i < Math.min(K, rankedIds.size()); i++) {
            int grade = grades.getOrDefault(rankedIds.get(i), 0);
            dcg += (Math.pow(2, grade) - 1) / (Math.log(i + 2) / Math.log(2));
        }
        List<Integer> ideal = new ArrayList<>(grades.values());
        ideal.sort((a, b) -> b - a);
        double idcg = 0;
        for (int i = 0; i < Math.min(K, ideal.size()); i++) {
            idcg += (Math.pow(2, ideal.get(i)) - 1) / (Math.log(i + 2) / Math.log(2));
        }
        return idcg == 0 ? 0 : dcg / idcg;
    }

    // --- searcher access (same REST contract as OcsSearchClient) -------------

    private boolean reachable() {
        return searchRaw("") != null;
    }

    @SuppressWarnings("unchecked")
    private List<String> searchTopIds(String query) {
        Map<String, Object> result = searchRaw(query);
        List<String> ids = new ArrayList<>();
        if (result == null || !(result.get("slices") instanceof List)) {
            return ids;
        }
        for (Object sliceObj : (List<Object>) result.get("slices")) {
            Map<String, Object> slice = (Map<String, Object>) sliceObj;
            if (slice.get("hits") instanceof List) {
                for (Object hitObj : (List<Object>) slice.get("hits")) {
                    Map<String, Object> document = (Map<String, Object>) ((Map<String, Object>) hitObj).get("document");
                    if (document != null && document.get("id") != null) {
                        ids.add(String.valueOf(document.get("id")));
                    }
                }
            }
        }
        return ids;
    }

    private Map<String, Object> searchRaw(String query) {
        try {
            return http.exchange(
                    SEARCH_URL + "/search-api/v1/search/" + INDEX + "?q={q}&limit=" + K,
                    HttpMethod.GET, null,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    }, query).getBody();
        } catch (Exception e) {
            return null;
        }
    }

    // --- judgment file ---------------------------------------------------------

    /** Resolve a repo path whether the test runs from the module dir or the repo root. */
    private static Path locate(String relative) {
        for (Path candidate : List.of(Path.of(relative), Path.of("litemall-goods-management", relative))) {
            if (Files.isReadable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static Map<String, Map<String, Integer>> parseJudgments(Path csv) throws IOException {
        Map<String, Map<String, Integer>> judgments = new LinkedHashMap<>();
        for (String line : Files.readAllLines(csv, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("query,")) {
                continue;
            }
            String[] parts = trimmed.split(",");
            if (parts.length == 3) {
                judgments.computeIfAbsent(parts[0].trim(), k -> new LinkedHashMap<>())
                        .put(parts[1].trim(), Integer.parseInt(parts[2].trim()));
            }
        }
        return judgments;
    }
}
