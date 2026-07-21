package org.linlinjava.litemall.order.infrastructure.services.cj;

import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjDisabledException;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.CjAuthFeignClient;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjAuthRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjAuthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Provides a valid CJ Dropshipping access token to the CJ order client, with an in-memory cache +
 * re-auth on (near-)expiry. The order service authenticates independently of goods-management.
 * Email + API key are config-driven ({@code spring.cjdropship.api.auth.*} ← {@code CJ_EMAIL} /
 * {@code CJ_API_KEY}); no secrets in code.
 *
 * <p><b>Disabled mode (Wave 8):</b> both credentials blank ⇒ the whole CJ ACL is DISABLED —
 * {@link #getValidToken()} throws a typed {@link LitemallCjDisabledException} without any
 * network call, every best-effort CJ facade degrades to its clean empty/false answer, the
 * placement + status-sync sweeps skip via {@link #isEnabled()}, and paid CJ orders are
 * retained for placement once the key appears. Half-configured (one of the two blank) is a
 * deployment mistake and fails the boot, mirroring the Stripe seam in
 * {@code FulfillmentSeamsConfiguration}.
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
    private final boolean enabled;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;
    private final Object lock = new Object();

    public CjTokenService(CjAuthFeignClient authClient,
                          @Value("${spring.cjdropship.api.auth.cj-email:}") String cjEmail,
                          @Value("${spring.cjdropship.api.auth.cj-api-key:}") String cjApiKey,
                          @Value("${spring.cjdropship.api.sandbox:false}") boolean sandbox) {
        this.authClient = authClient;
        this.cjEmail = cjEmail;
        this.cjApiKey = cjApiKey;
        boolean hasEmail = StringUtils.hasText(cjEmail);
        boolean hasKey = StringUtils.hasText(cjApiKey);
        if (hasEmail != hasKey) {
            // Half-configured is a deployment mistake, not a disabled deployment: refusing to
            // boot beats silently running with CJ off (same stance as the Stripe seam).
            throw new IllegalStateException("CJ dropshipping is half-configured: "
                    + (hasKey ? "CJ_API_KEY is set but CJ_EMAIL is blank" : "CJ_EMAIL is set but CJ_API_KEY is blank")
                    + " — set both to enable CJ, or neither to run with CJ disabled.");
        }
        this.enabled = hasEmail;
        if (this.enabled) {
            LOGGER.info("CJ dropshipping ENABLED for {} (sandbox={})", cjEmail, sandbox);
        } else {
            LOGGER.info("CJ dropshipping DISABLED (CJ_EMAIL/CJ_API_KEY empty) — paid CJ orders "
                    + "are retained and will be placed automatically once credentials appear");
        }
    }

    /** Whether CJ credentials are configured; when false every CJ call is short-circuited. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * A non-expired CJ access token, refreshing via {@code getAccessToken} when needed.
     *
     * @throws LitemallCjDisabledException when the CJ ACL is disabled (no credentials) —
     *         thrown without any network call, so disabled deployments never hammer CJ auth.
     */
    public String getValidToken() {
        if (!enabled) {
            throw new LitemallCjDisabledException(
                    "CJ ACL disabled (CJ_EMAIL/CJ_API_KEY empty) — fulfilment retained for retry");
        }
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
