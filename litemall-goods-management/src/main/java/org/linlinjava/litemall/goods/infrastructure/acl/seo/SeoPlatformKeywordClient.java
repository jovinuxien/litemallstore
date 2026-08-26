package org.linlinjava.litemall.goods.infrastructure.acl.seo;

import org.linlinjava.litemall.goods.application.seo.KeywordResearchProvider;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSeoResearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * seo_plateform research-API adapter: the ONLY place in this service that knows the platform
 * exists.
 *
 * <p>Translation, which is the whole job of this class:
 * <ul>
 *   <li>{@code searchVolume} → {@code monthlySearches}, {@code keywordDifficulty} → {@code difficulty};</li>
 *   <li>{@code locationCode}/{@code languageCode} are supplied from configuration and never
 *       cross the port — the domain asks for a term, not for a term in a market;</li>
 *   <li>{@code cpcUsd}, {@code tenantId}, {@code seedKeyword}, {@code fetchedAt} and the row id are
 *       dropped. They are the platform's billing and provenance concerns; a catalogue copywriter
 *       has no use for them and importing them would leak the provider's model inward.</li>
 * </ul>
 *
 * <p>Endpoint: {@code GET /srv/research/keywords/suggestions?seed=&locationCode=&languageCode=},
 * routed by the platform gateway to seo-research-service, which answers a JSON array of keyword
 * metric rows.
 *
 * <p>Fail-soft everywhere, per {@link KeywordResearchProvider}: disabled, unconfigured, no token,
 * transport failure, spend cap reached (the platform answers {@code 402 Payment Required}) and an
 * unreadable envelope all return an empty list. Nothing here throws at the caller.
 */
@Component
@ConditionalOnProperty(prefix = "litemall.seo-research", name = "source", havingValue = "platform")
public class SeoPlatformKeywordClient implements KeywordResearchProvider {

    private static final Logger log = LoggerFactory.getLogger(SeoPlatformKeywordClient.class);

    private static final String SUGGESTIONS_PATH = "/srv/research/keywords/suggestions";

    private final RestTemplate restTemplate;
    private final LitemallSeoResearchProperties properties;
    private final SeoAccessTokenSupplier tokens;

    public SeoPlatformKeywordClient(LitemallSeoResearchProperties properties,
                                    SeoAccessTokenSupplier tokens,
                                    RestTemplateBuilder builder) {
        this.properties = properties;
        this.tokens = tokens;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .build();
    }

    @Override
    public List<KeywordDemand> demandFor(String seed, int limit) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        if (seed == null || seed.isBlank() || properties.getBaseUrl() == null
                || properties.getBaseUrl().isBlank()) {
            return List.of();
        }
        String token = tokens.token();
        if (token == null) {
            return List.of();
        }

        URI uri = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl())
                .path(SUGGESTIONS_PATH)
                .queryParam("seed", seed.trim())
                .queryParam("locationCode", properties.getLocationCode())
                .queryParam("languageCode", properties.getLanguageCode())
                .build()
                .encode()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = restTemplate.exchange(
                    uri, org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers), List.class).getBody();
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            return rows.stream()
                    .map(SeoPlatformKeywordClient::toDemand)
                    .filter(d -> d != null && d.term() != null && !d.term().isBlank())
                    // Highest demand first. Nulls mean UNKNOWN, and an unknown term sorts last
                    // rather than as zero — it may well be the best term in the set.
                    .sorted(Comparator.comparing(
                            (KeywordDemand d) -> d.monthlySearches() == null
                                    ? Integer.MIN_VALUE : d.monthlySearches())
                            .reversed())
                    .limit(Math.max(1, limit))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("seo research: keyword lookup for seed '{}' failed ({}) — continuing without demand data",
                    seed, e.getMessage());
            return List.of();
        }
    }

    /** Wire row → domain record. Returns null on a row that carries no usable term. */
    private static KeywordDemand toDemand(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object term = row.get("keyword");
        if (!(term instanceof String keyword)) {
            return null;
        }
        return new KeywordDemand(
                keyword,
                asInteger(row.get("searchVolume")),
                asDecimal(row.get("competition")),
                asInteger(row.get("keywordDifficulty")));
    }

    // Absent and unparseable both become null, never 0 — see KeywordDemand's contract.
    private static Integer asInteger(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private static BigDecimal asDecimal(Object value) {
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return null;
    }
}
