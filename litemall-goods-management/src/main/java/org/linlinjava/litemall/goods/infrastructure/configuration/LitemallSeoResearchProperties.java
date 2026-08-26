package org.linlinjava.litemall.goods.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * seo_plateform research-API configuration for the SEO ACL.
 *
 * <p>Hosts are NOT defaulted in Java, matching {@link LitemallSearchProperties}: they bind from
 * {@code litemall.seo-research.*} and stay profile-overridable, so no environment's URL is
 * hardcoded here.
 *
 * <p>{@code enabled} defaults to FALSE, and that default carries more weight than for the other
 * connectors. The platform behind this seam buys keyword data from a pay-as-you-go provider, so
 * switching it on is what starts spending money — the same reason the platform's own
 * {@code seo.research.dataforseo.enabled} defaults to false. A configured URL with the switch off
 * costs nothing.
 */
@ConfigurationProperties(prefix = "litemall.seo-research")
public class LitemallSeoResearchProperties {

    /**
     * Where keyword demand comes from.
     *
     * <ul>
     *   <li>{@code file} (default) — a CSV exported from an earlier research run. Costs
     *       nothing, works with no network and no identity provider, and is the honest
     *       default: the data was already bought.</li>
     *   <li>{@code platform} — live calls to seo_plateform. Buys uncached seeds.</li>
     *   <li>{@code none} — a no-op provider that always answers empty.</li>
     * </ul>
     *
     * Both real sources sit behind the same port, so callers never learn which is active.
     */
    private String source = "file";

    /**
     * CSV to read when {@code source=file} — the cleaned export
     * ({@code category-keywords-clean.csv}), not the raw one: the raw file carries
     * permutation groups that make one term look like six.
     *
     * <p>Absent or unreadable is not fatal; the provider serves nothing and says so once.
     */
    private String file;

    /**
     * Off unless deliberately switched on: enabling this is what starts spending.
     *
     * <p>Applies to {@code source=platform} only. A file source reads data already paid
     * for, so it is not gated by this.
     */
    private boolean enabled = false;

    /** Base URL of the seo_plateform gateway, e.g. {@code http://localhost:9000}. */
    private String baseUrl;

    /** Keycloak token endpoint for the seo realm (client_credentials). */
    private String tokenUri;

    /** Service-account client id in the seo realm. */
    private String clientId;

    /** Service-account secret. Never defaulted — absent means the ACL stays dark. */
    private String clientSecret;

    /**
     * Provider location code for the market being written for. 2276 = Germany, matching where the
     * store actually fulfils from; 2840 = United States is the platform's own default and is
     * almost certainly wrong here. A wrong location is a CHARGED call that returns plausible data
     * for the wrong country, which is the kind of error only someone who knows the market catches.
     */
    private int locationCode = 2276;

    /** Provider language code. The catalogue is English, so {@code en} even in a DE location. */
    private String languageCode = "en";

    /** Rows requested per seed. The provider bills per seed, not per row. */
    private int limit = 100;

    /** Connect timeout, seconds. */
    private int connectTimeoutSeconds = 3;

    /**
     * Read timeout, seconds. Generous on purpose: an uncached seed is a live third-party purchase
     * and routinely takes several seconds. Timing out does not refund the call.
     */
    private int readTimeoutSeconds = 30;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getTokenUri() {
        return tokenUri;
    }

    public void setTokenUri(String tokenUri) {
        this.tokenUri = tokenUri;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public int getLocationCode() {
        return locationCode;
    }

    public void setLocationCode(int locationCode) {
        this.locationCode = locationCode;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }
}
