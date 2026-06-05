package org.linlinjava.litemall.goods.infrastructure.acl.ocs;

import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * ACL adapter to the OCS suggest REST service (port 8081 by default).
 *
 * <p>API contract verified at runtime against {@code commerceexperts/
 * ocs-suggest-service}: {@code GET {suggest-url}/suggest-api/v1/{index}/suggest
 * ?userQuery={prefix}} returns a JSON array of objects
 * {@code [{"phrase":"…","type":"…","payload":{…}}]}; we project each
 * {@code phrase}. (The previous {@code /suggest/{index}?q=} path + array-of-
 * strings assumption was wrong and always returned empty.)
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

    @SuppressWarnings("unchecked")
    public List<String> suggest(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return Collections.emptyList();
        }
        String url = UriComponentsBuilder
                .fromHttpUrl(properties.getSuggestUrl())
                .pathSegment("suggest-api", "v1", properties.getIndexName(), "suggest")
                .queryParam("userQuery", prefix)
                .toUriString();
        try {
            List<Map<String, Object>> entries = restTemplate.getForObject(url, List.class);
            if (entries == null) {
                return Collections.emptyList();
            }
            List<String> phrases = new ArrayList<>(entries.size());
            for (Map<String, Object> entry : entries) {
                Object phrase = entry.get("phrase");
                if (phrase != null) {
                    phrases.add(phrase.toString());
                }
            }
            return phrases;
        } catch (RestClientException e) {
            LOGGER.warn("OCS suggest failed for prefix={} ({}): {}", prefix, url, e.getMessage());
            return Collections.emptyList();
        }
    }
}
