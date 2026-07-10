package org.linlinjava.litemall.promotion.infrastructure.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed, environment-overridable settings for the Matomo Reporting-API ACL
 * (Phase 3 analytics → statistics). Bound from {@code litemall.promotion.matomo.*}.
 *
 * <p>Disabled by default and every value sourced via {@code ${ENV:default}} in
 * {@code application.yml} — the service boots without a Matomo instance and the
 * adapter degrades to an empty population when {@code enabled=false} or the host
 * is unreachable. No host/token is hardcoded in Java or committed.
 */
@Component
@ConfigurationProperties(prefix = "litemall.promotion.matomo")
@Getter
@Setter
public class MatomoProperties {

    /** When false the Matomo adapter is a no-op (returns an empty population). */
    private boolean enabled = false;

    /** Matomo base URL (e.g. {@code https://analytics.example.com}); from env, never hardcoded. */
    private String baseUrl;

    /** Reporting-API auth token ({@code token_auth}); from env, never committed. */
    private String authToken;

    /** Matomo site id ({@code idSite}) to report on. */
    private int siteId = 1;

    /**
     * Reporting-API method yielding the per-user rows (label = litemall user id).
     * Default targets Matomo's User ID report; override to a custom report that
     * also carries goal revenue/conversions per user.
     */
    private String reportMethod = "UserId.getUsers";

    /** Reporting period (Matomo {@code period}: day/week/month/range). */
    private String period = "month";

    /** Reporting date/range expression (Matomo {@code date}, e.g. {@code previous12}). */
    private String date = "previous12";

    /** Max rows pulled from a report ({@code filter_limit}; -1 = no limit). */
    private int filterLimit = 5000;
}
