package org.linlinjava.litemall.promotion.infrastructure.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Typed, environment-overridable settings for the Wave-17 Postiz publishing
 * vertical. Bound from {@code litemall.postiz.*}.
 *
 * <p>The feature is env-gated on BOTH {@code base-url}
 * ({@code LITEMALL_POSTIZ_BASE_URL}) and {@code api-key}
 * ({@code LITEMALL_POSTIZ_API_KEY}): either absent ⇒ every endpoint answers a
 * typed "not configured" errno and the admin panel stays hidden. Enabling later
 * is an env change + restart — no rebuild.
 */
@Component
@ConfigurationProperties(prefix = "litemall.postiz")
@Getter
@Setter
public class PostizProperties {

    /**
     * Postiz public-API base incl. the version segment, e.g.
     * {@code http://localhost:4007/api/public/v1}. Deliberately no default —
     * an unset value keeps the whole feature dark.
     */
    private String baseUrl;

    /**
     * Public-API key from Postiz Settings → Public API. Sent as a BARE
     * {@code Authorization} header value — Postiz's REST API takes no
     * {@code Bearer} prefix.
     */
    private String apiKey;

    /**
     * Public storefront origin for composed post links AND image URLs — the
     * platforms' servers must be able to fetch both, so even the dev pilot
     * points at the live storefront.
     */
    private String publicBaseUrl = "https://trovemo.com";

    /** Channel-list cache TTL; Postiz's shipped compose throttles at API_LIMIT=30/h. */
    private long channelsCacheTtlMs = 300_000;

    /**
     * Max products per publish batch. One Postiz call per product, so the cap
     * keeps a single batch within Postiz's hourly throttle (30/h shipped).
     */
    private int batchCap = 25;

    /** Look-back window for the non-blocking "posted N days ago" dedup warning. */
    private int dedupWarnDays = 7;

    /** Both the base URL and the API key must be present for the feature to light up. */
    public boolean isConfigured() {
        return StringUtils.hasText(baseUrl) && StringUtils.hasText(apiKey);
    }
}
