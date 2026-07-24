package org.linlinjava.litemall.goods.infrastructure.acl.ocs;

import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSearchProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Map;

/**
 * OCS search-service REST adapter. The OCS search service exposes
 * {@code GET /search-api/v1/search/<index>?q=<query>&offset=&limit=&sort=&<facetField>=<value>}
 * (and the suggest service is wrapped separately by {@link OcsSuggestClient}). The response
 * is the OCS {@code SearchResult} JSON; callers map it to their own DTO.
 *
 * <p>Filter and sort syntax verified against the live searcher: term filter {@code brand=<value>}
 * (multi-select = comma-joined in ONE param, OR semantics), interval filter {@code price=<min>,<max>},
 * category filter {@code category_ids=<id>}/{@code category_names=<name>}; sort {@code sort=<field>}
 * ascending, {@code sort=-<field>} descending. Filter keys are whitelisted by the caller.
 */
@Component
public class OcsSearchClient {

    private final RestTemplate restTemplate;
    private final LitemallSearchProperties properties;

    public OcsSearchClient(LitemallSearchProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        this.restTemplate = builder
                .rootUri(properties.getSearchUrl())
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    public OcsSearchResult search(String query, int offset, int size, String sort, Map<String, String> filters) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath("/search-api/v1/search/{index}")
                .queryParam("q", query == null ? "" : query)
                .queryParam("offset", offset)
                .queryParam("limit", size)
                // Request per-hit highlighting. The searcher deployed today ignores this param
                // (live-probed 2026-07-24: response byte-identical with/without, no ResultHit
                // highlight field upstream) — SearchService compensates app-side — but sending it
                // keeps us forward-compatible with an OCS build that honours it.
                .queryParam("highlight", "true");
        if (sort != null && !sort.isBlank()) {
            builder.queryParam("sort", sort);
        }
        if (filters != null) {
            for (Map.Entry<String, String> filter : filters.entrySet()) {
                if (filter.getValue() != null && !filter.getValue().isBlank()) {
                    builder.queryParam(filter.getKey(), filter.getValue());
                }
            }
        }
        // Keep a String template so the RestTemplate's rootUri (the search host) is applied and the
        // template+values are encoded; passing a pre-built URI would bypass rootUri (see OcsSuggestClient).
        String uri = builder.build().toUriString();
        return restTemplate.getForObject(uri, OcsSearchResult.class, properties.getIndexName());
    }
}
