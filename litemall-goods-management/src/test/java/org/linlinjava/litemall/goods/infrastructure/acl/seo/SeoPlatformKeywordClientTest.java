package org.linlinjava.litemall.goods.infrastructure.acl.seo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.goods.application.seo.KeywordResearchProvider.KeywordDemand;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSeoResearchProperties;
import org.mockito.Mockito;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Translation and degradation behaviour of the seo_plateform ACL. The far side is never contacted:
 * these pin the mapping and the fail-soft contract, which are the two things a provider change
 * would break silently.
 */
public class SeoPlatformKeywordClientTest {

    private RestTemplate restTemplate;
    private SeoAccessTokenSupplier tokens;
    private LitemallSeoResearchProperties properties;
    private SeoPlatformKeywordClient client;

    @BeforeEach
    void setup() {
        restTemplate = Mockito.mock(RestTemplate.class);
        tokens = Mockito.mock(SeoAccessTokenSupplier.class);
        properties = new LitemallSeoResearchProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://localhost:9000");

        RestTemplateBuilder builder = Mockito.mock(RestTemplateBuilder.class);
        Mockito.when(builder.setConnectTimeout(any())).thenReturn(builder);
        Mockito.when(builder.setReadTimeout(any())).thenReturn(builder);
        Mockito.when(builder.build()).thenReturn(restTemplate);

        Mockito.when(tokens.token()).thenReturn("a-token");
        client = new SeoPlatformKeywordClient(properties, tokens, builder);
    }

    private void stubRows(List<Map<String, Object>> rows) {
        Mockito.when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET),
                        any(HttpEntity.class), eq(List.class)))
                .thenReturn(ResponseEntity.ok(rows));
    }

    private static Map<String, Object> row(String keyword, Object volume, Object competition,
                                           Object difficulty) {
        return Map.of("keyword", keyword,
                "searchVolume", volume,
                "competition", competition,
                "keywordDifficulty", difficulty);
    }

    @Test
    public void mapsPlatformRowOntoDomainVocabulary() {
        stubRows(List.of(row("led ceiling light", 8100, 0.42, 31)));

        List<KeywordDemand> demand = client.demandFor("Indoor Lighting", 10);

        assertThat(demand).hasSize(1);
        assertThat(demand.get(0).term()).isEqualTo("led ceiling light");
        assertThat(demand.get(0).monthlySearches()).isEqualTo(8100);
        assertThat(demand.get(0).competition()).isEqualByComparingTo(new BigDecimal("0.42"));
        assertThat(demand.get(0).difficulty()).isEqualTo(31);
    }

    @Test
    public void ordersByDemandAndSortsUnknownVolumeLastRatherThanAsZero() {
        stubRows(List.of(
                row("low volume term", 10, 0.1, 5),
                // No searchVolume key at all — unknown, not zero.
                Map.of("keyword", "unknown volume term"),
                row("high volume term", 9000, 0.9, 70)));

        List<KeywordDemand> demand = client.demandFor("Tools", 10);

        assertThat(demand).extracting(KeywordDemand::term)
                .containsExactly("high volume term", "low volume term", "unknown volume term");
        assertThat(demand.get(2).monthlySearches()).isNull();
    }

    @Test
    public void honoursTheRequestedLimit() {
        stubRows(List.of(row("a", 5, 0.1, 1), row("b", 4, 0.1, 1), row("c", 3, 0.1, 1)));

        assertThat(client.demandFor("Tools", 2)).hasSize(2);
    }

    @Test
    public void returnsEmptyWhenDisabledAndNeverCallsTheApi() {
        properties.setEnabled(false);

        assertThat(client.demandFor("Tools", 10)).isEmpty();
        Mockito.verifyNoMoreInteractions(restTemplate);
    }

    @Test
    public void returnsEmptyWhenNoTokenCouldBeObtained() {
        Mockito.when(tokens.token()).thenReturn(null);

        assertThat(client.demandFor("Tools", 10)).isEmpty();
        Mockito.verifyNoMoreInteractions(restTemplate);
    }

    @Test
    public void degradesToEmptyOnTransportFailureRatherThanThrowing() {
        Mockito.when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET),
                        any(HttpEntity.class), eq(List.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        assertThat(client.demandFor("Tools", 10)).isEmpty();
    }

    @Test
    public void dropsRowsCarryingNoUsableTerm() {
        stubRows(List.of(Map.of("searchVolume", 100), row("real term", 50, 0.2, 10)));

        assertThat(client.demandFor("Tools", 10))
                .extracting(KeywordDemand::term)
                .containsExactly("real term");
    }

    @Test
    public void blankSeedIsNotWorthAPaidCall() {
        assertThat(client.demandFor("   ", 10)).isEmpty();
        Mockito.verifyNoMoreInteractions(restTemplate);
    }
}
