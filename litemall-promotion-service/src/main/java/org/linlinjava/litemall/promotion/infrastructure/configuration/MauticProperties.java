package org.linlinjava.litemall.promotion.infrastructure.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed, environment-overridable settings for the Mautic REST-API ACL (Phase 3
 * automation → delivery). Bound from {@code litemall.promotion.mautic.*}.
 *
 * <p>Disabled by default; base URL and Basic-Auth credentials come via
 * {@code ${ENV:default}} so nothing is hardcoded or committed. When
 * {@code enabled=false} or Mautic is unreachable the delivery adapter is a
 * best-effort no-op (logged) and never unwinds the committed campaign evaluation.
 */
@Component
@ConfigurationProperties(prefix = "litemall.promotion.mautic")
@Getter
@Setter
public class MauticProperties {

    /** When false the Mautic delivery adapter is a no-op. */
    private boolean enabled = false;

    /** Mautic base URL (e.g. {@code https://mautic.example.com}); from env, never hardcoded. */
    private String baseUrl;

    /** Basic-Auth username; from env, never committed. */
    private String username;

    /** Basic-Auth password; from env, never committed. */
    private String password;

    /**
     * Custom Mautic contact field (alias) holding the litemall user id, used to
     * resolve/segment contacts by our id. Configurable to match the Mautic setup.
     */
    private String userIdFieldAlias = "litemall_user_id";

    /** Prefix for the per-campaign Mautic segment alias ({@code <prefix><campaignId>}). */
    private String segmentAliasPrefix = "litemall-campaign-";
}
