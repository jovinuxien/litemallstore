package org.linlinjava.litemall.goods.infrastructure.acl.ocs;

import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

/**
 * ACL adapter to the OCS suggest REST service (port 8081 by default).
 *
 * <p><b>API contract — runtime verification needed.</b> Assumed
 * {@code GET {suggest-url}/suggest/{index-name}?q={prefix}} returning a JSON
 * array of suggestion strings.
 */
@Component
public class OcsSuggestClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OcsSuggestClient.class);

    // See OcsIndexerClient — owned per-client to avoid bean conflict with the
    // shared core RestTemplate.
    private final RestTemplate restTemplate = new RestTemplate();
    private final LitemallSearchProperties properties;

    public OcsSuggestClient(LitemallSearchProperties properties) {
        this.properties = properties;
    }

    /**
     * TODO: verify exact path + query-parameter names against OCS OpenAPI
     * doc at runtime.
     */
    @SuppressWarnings("unchecked")
    public List<String> suggest(String prefix) {
        String url = UriComponentsBuilder
                .fromHttpUrl(properties.getSuggestUrl())
                .pathSegment("suggest", properties.getIndexName())
                .queryParam("q", prefix)
                .toUriString();
        try {
            return restTemplate.getForObject(url, List.class);
        } catch (RestClientException e) {
            LOGGER.warn("OCS suggest failed for prefix={} ({}): {}", prefix, url, e.getMessage());
            return Collections.emptyList();
        }
    }
}
