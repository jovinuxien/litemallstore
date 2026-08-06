package org.linlinjava.litemall.promotion.infrastructure.acl.goods;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Supplies a service-to-service machine JWT for outbound calls to
 * goods-management, which sits behind the svcsecurity resource-server and
 * rejects unauthenticated requests (401). This is the litemall-order recipe
 * ({@code GoodsMachineTokenProvider} there): promotion is only an OAuth2
 * resource-server, so it fetches a {@code client_credentials} token directly
 * from the authserver token endpoint and caches it until just before expiry.
 * goods-management only checks the JWT signature against the shared authserver
 * JWKS, not which client minted it.
 */
@Component
public class GoodsMachineTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(GoodsMachineTokenProvider.class);

    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public GoodsMachineTokenProvider(
            @Value("${litemall.goods.auth.token-uri:http://localhost:8089/oauth2/token}") String tokenUri,
            @Value("${litemall.goods.auth.client-id:gateway-api}") String clientId,
            @Value("${litemall.goods.auth.client-secret:gateway-api-dev-secret}") String clientSecret) {
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** Returns a valid bearer token, refreshing if missing or within 30s of expiry. */
    public synchronized String getToken() {
        if (cachedToken != null && Instant.now().isBefore(expiresAt.minusSeconds(30))) {
            return cachedToken;
        }
        String basic = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUri))
                .timeout(Duration.ofSeconds(3))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();
        try {
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("token endpoint returned HTTP " + resp.statusCode());
            }
            JsonNode body = mapper.readTree(resp.body());
            cachedToken = body.path("access_token").asText(null);
            long expiresIn = body.path("expires_in").asLong(300);
            if (cachedToken == null) {
                throw new IllegalStateException("no access_token in token response");
            }
            expiresAt = Instant.now().plusSeconds(expiresIn);
            return cachedToken;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            cachedToken = null;
            expiresAt = Instant.EPOCH;
            log.warn("machine token fetch from {} failed: {}", tokenUri, e.getMessage());
            throw new IllegalStateException("cannot obtain machine token: " + e.getMessage(), e);
        }
    }
}
