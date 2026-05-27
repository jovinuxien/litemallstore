package org.linlinjava.litemall.goods.infrastructure.acl.ocs;

import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.Map;

/**
 * ACL adapter to the OCS searcher REST service (port 8534 by default).
 * Returns the raw response map; mapping to the goods-list DTO happens
 * outside this adapter.
 *
 * <p><b>API contract — runtime verification needed.</b> Assumed
 * {@code GET {search-url}/search/{index-name}?q={query}&offset={offset}&limit={limit}}.
 */
@Component
public class OcsSearchClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OcsSearchClient.class);

    // See OcsIndexerClient — owned per-client to avoid bean conflict with the
    // shared core RestTemplate.
    private final RestTemplate restTemplate = new RestTemplate();
    private final LitemallSearchProperties properties;

    public OcsSearchClient(LitemallSearchProperties properties) {
        this.properties = properties;
    }

    /**
     * TODO: verify exact path + query-parameter names against OCS OpenAPI
     * doc at runtime.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> search(String query, int offset, int limit) {
        String url = UriComponentsBuilder
                .fromHttpUrl(properties.getSearchUrl())
                .pathSegment("search", properties.getIndexName())
                .queryParam("q", query)
                .queryParam("offset", offset)
                .queryParam("limit", limit)
                .toUriString();
        try {
            return restTemplate.getForObject(url, Map.class);
        } catch (RestClientException e) {
            LOGGER.warn("OCS search failed for q={} ({}): {}", query, url, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
