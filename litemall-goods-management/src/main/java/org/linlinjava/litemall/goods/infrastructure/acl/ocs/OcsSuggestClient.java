package org.linlinjava.litemall.goods.infrastructure.acl.ocs;

import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSearchProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * OCS suggest-service REST adapter. The suggest service exposes
 * {@code GET /suggest-api/v1/<index>/suggest?userQuery=<term>} and returns a
 * list of suggestion entries.
 */
@Component
public class OcsSuggestClient {

    private static final ParameterizedTypeReference<List<OcsSuggestion>> SUGGEST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestTemplate restTemplate;
    private final LitemallSearchProperties properties;

    public OcsSuggestClient(LitemallSearchProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        this.restTemplate = builder
                .rootUri(properties.getSuggestUrl())
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    public List<OcsSuggestion> suggest(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return Collections.emptyList();
        }
        // Pass a String URL template (not a pre-built java.net.URI): RestTemplate's
        // configured rootUri (the suggest host) is applied only to String templates,
        // and it expands + encodes the {index}/{q} variables for us. Building a URI
        // here would yield a host-less path and fail with "Target host is not specified".
        List<OcsSuggestion> body = restTemplate
                .exchange("/suggest-api/v1/{index}/suggest?userQuery={q}",
                        HttpMethod.GET, null, SUGGEST_TYPE,
                        properties.getIndexName(), userQuery)
                .getBody();
        return body == null ? Collections.emptyList() : body;
    }
}
