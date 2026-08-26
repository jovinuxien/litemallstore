package org.linlinjava.litemall.goods.infrastructure.acl.seo;

import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSeoResearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Mints and caches the {@code client_credentials} access token the seo_plateform research API
 * requires.
 *
 * <p>Why this exists rather than {@code spring-security-oauth2-client}: litemall-svcsecurity gives
 * every service INBOUND machine-token validation ({@code MachineTokenUserContextFilter}) but no
 * outbound minting, and this is the only outbound machine call in the service. Pulling a new
 * Spring module in for one caller would spread a foreign identity provider's concerns across the
 * service; keeping the grant here keeps the seo realm entirely inside the ACL, which is the point
 * of the ACL.
 *
 * <p>The token carries a hardcoded {@code tenant} claim from the realm's mapper. That claim is
 * load-bearing on the far side: the research service keys its cache, its cost ledger and its
 * monthly spend cap on the tenant, and a token without the claim is answered with an empty result
 * rather than an error — data silently missing rather than a failure anyone would notice.
 */
@Component
public class SeoAccessTokenSupplier {

    private static final Logger log = LoggerFactory.getLogger(SeoAccessTokenSupplier.class);

    /**
     * Renew this long before the stated expiry. A token that is valid when checked but expires in
     * flight fails the request it was fetched for, and the retry buys nothing back.
     */
    private static final Duration EARLY_RENEWAL = Duration.ofSeconds(30);

    private final RestTemplate restTemplate;
    private final LitemallSeoResearchProperties properties;

    private volatile String cachedToken;
    private volatile Instant cachedUntil = Instant.EPOCH;

    public SeoAccessTokenSupplier(LitemallSeoResearchProperties properties,
                                  RestTemplateBuilder builder) {
        this.properties = properties;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .build();
    }

    /**
     * A currently valid bearer token, or null when one cannot be obtained.
     *
     * <p>Null rather than an exception: the provider contract above this is fail-soft, and an
     * identity provider being down is exactly the case that must degrade to "no keyword data"
     * rather than break a catalogue operation.
     */
    public synchronized String token() {
        if (cachedToken != null && Instant.now().isBefore(cachedUntil)) {
            return cachedToken;
        }
        if (isBlank(properties.getTokenUri()) || isBlank(properties.getClientId())
                || isBlank(properties.getClientSecret())) {
            log.warn("seo research: token endpoint or credentials not configured — keyword data unavailable");
            return null;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.postForObject(
                    properties.getTokenUri(), new HttpEntity<>(form, headers), Map.class);
            if (body == null || !(body.get("access_token") instanceof String token) || token.isBlank()) {
                log.warn("seo research: token endpoint returned no access_token — keyword data unavailable");
                return null;
            }
            long expiresIn = body.get("expires_in") instanceof Number n ? n.longValue() : 60L;
            Duration life = Duration.ofSeconds(expiresIn).minus(EARLY_RENEWAL);
            // A token whose whole life is shorter than the renewal margin still gets used once;
            // treating it as already expired would loop on the token endpoint forever.
            cachedUntil = Instant.now().plus(life.isNegative() ? Duration.ofSeconds(1) : life);
            cachedToken = token;
            return cachedToken;
        } catch (RuntimeException e) {
            // Do not cache the failure: the next call should try again rather than stay dark for
            // the lifetime of the process because the IdP restarted once.
            cachedToken = null;
            cachedUntil = Instant.EPOCH;
            log.warn("seo research: could not obtain access token ({}) — keyword data unavailable",
                    e.getMessage());
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
