package org.linlinjava.litemall.order.infrastructure.services.cj;

import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.CjAuthFeignClient;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjAuthRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjAuthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Provides a valid CJ Dropshipping access token to the CJ order client, with an in-memory cache +
 * re-auth on (near-)expiry. The order service authenticates independently of goods-management.
 * Email + API key are config-driven ({@code spring.cjdropship.api.auth.*}); no secrets in code.
 */
@Service
public class CjTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CjTokenService.class);
    /** Re-auth this long before the reported expiry to avoid edge-of-expiry failures. */
    private static final long EXPIRY_SAFETY_SECONDS = 60;
    /** Conservative TTL used when CJ's expiry date can't be parsed. */
    private static final long DEFAULT_TTL_SECONDS = 6 * 60 * 60;

    private final CjAuthFeignClient authClient;
    private final String cjEmail;
    private final String cjApiKey;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;
    private final Object lock = new Object();

    public CjTokenService(CjAuthFeignClient authClient,
                          @Value("${spring.cjdropship.api.auth.cj-email}") String cjEmail,
                          @Value("${spring.cjdropship.api.auth.cj-api-key}") String cjApiKey) {
        this.authClient = authClient;
        this.cjEmail = cjEmail;
        this.cjApiKey = cjApiKey;
    }

    /** A non-expired CJ access token, refreshing via {@code getAccessToken} when needed. */
    public String getValidToken() {
        if (isValid()) {
            return cachedToken;
        }
        synchronized (lock) {
            if (isValid()) {
                return cachedToken;
            }
            refresh();
            return cachedToken;
        }
    }

    private boolean isValid() {
        return cachedToken != null
                && Instant.now().isBefore(expiresAt.minusSeconds(EXPIRY_SAFETY_SECONDS));
    }

    private void refresh() {
        CjAuthResponse response = authClient.getAccessToken(new CjAuthRequest(cjEmail, cjApiKey));
        if (response == null || !response.isResult() || response.getData() == null
                || response.getData().getAccessToken() == null) {
            String message = response == null ? "null response" : response.getMessage();
            throw new IllegalStateException("CJ authentication failed: " + message);
        }
        cachedToken = response.getData().getAccessToken();
        expiresAt = parseExpiry(response.getData().getAccessTokenExpiryDate());
        LOGGER.info("CJ access token refreshed; expires at {}", expiresAt);
    }

    private Instant parseExpiry(String raw) {
        Instant fallback = Instant.now().plusSeconds(DEFAULT_TTL_SECONDS);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim();
        try {
            // e.g. "2025-06-13T10:24:30+08:00"
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException ignored) {
            // e.g. "2025-06-13 10:24:30" (CJ's common format, assume the local zone)
        }
        try {
            return java.time.LocalDateTime
                    .parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .atZone(java.time.ZoneId.systemDefault()).toInstant();
        } catch (RuntimeException ignored) {
            LOGGER.warn("Unparseable CJ token expiry '{}'; using default TTL", raw);
            return fallback;
        }
    }
}
