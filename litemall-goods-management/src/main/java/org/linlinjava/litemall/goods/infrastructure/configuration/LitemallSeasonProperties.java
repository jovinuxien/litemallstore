package org.linlinjava.litemall.goods.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Knobs for seasonal candidacy ({@code litemall.seasons.*}).
 *
 * <p>Every key has an EXPLICIT placeholder in {@code application.yml} and a compose passthrough.
 * That is not ceremony: {@code LITEMALL_GOODS_PRICE_FLOOR} was once set in {@code .env.prod} with
 * no passthrough in the compose block, so every surface reported the floor as configured while the
 * container ran with it at 0. Verify these bind INSIDE the container, not just in the file.
 */
@Component
@ConfigurationProperties(prefix = "litemall.seasons")
public class LitemallSeasonProperties {

    /** Master kill-switch. False ⇒ no scoring, no membership, empty {@code seasons} everywhere. */
    private boolean enabled = true;

    /**
     * False ⇒ every candidate stays {@code proposed} and NOTHING reaches a page, however well it
     * scores. The escape hatch if automatic publishing ever misbehaves: a container recreate, no
     * rebuild, and no data is lost — the scores are still there to publish later.
     */
    private boolean autoPublishEnabled = true;

    /** Minimum tier that publishes automatically: {@code hot} is stricter, {@code watch} looser. */
    private String autoTier = "featured";

    /** Most products one season may publish. Applied on READ, so re-runs stay idempotent. */
    private int perSeasonCap = 24;

    /** Nightly pass: 04:35, after the 04:30 promo scorer and before the 04:45 stats rollup. */
    private String cron = "0 35 4 * * *";

    /**
     * Most index hits to consider per season term before the gates run. Bounds the sweep; whatever
     * it drops is LOGGED rather than silently truncated.
     */
    private int candidateScanLimit = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoPublishEnabled() {
        return autoPublishEnabled;
    }

    public void setAutoPublishEnabled(boolean autoPublishEnabled) {
        this.autoPublishEnabled = autoPublishEnabled;
    }

    public String getAutoTier() {
        return autoTier;
    }

    public void setAutoTier(String autoTier) {
        this.autoTier = autoTier;
    }

    public int getPerSeasonCap() {
        return perSeasonCap;
    }

    public void setPerSeasonCap(int perSeasonCap) {
        this.perSeasonCap = perSeasonCap;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public int getCandidateScanLimit() {
        return candidateScanLimit;
    }

    public void setCandidateScanLimit(int candidateScanLimit) {
        this.candidateScanLimit = candidateScanLimit;
    }
}
