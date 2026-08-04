package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * Server-side lawful-basis audit row for a consent choice
 * (table {@code litemall_consent_record}, V49).
 *
 * <p>Contract: {@code doc/behavioral-events.md}. Append-only; a visitor's current
 * state is the newest row. {@code visitorId} is NULL for a denial that never had
 * identity minted (strict prior consent — denial has no identity by design).
 * Plain POJO, hand-maintained, no {@code Example} support.
 */
public class LitemallConsentRecord {

    public static final String CHOICE_GRANTED = "granted";
    public static final String CHOICE_DENIED = "denied";
    public static final String SCOPE_ANALYTICS = "analytics";

    private Integer id;
    private String visitorId;
    private Integer userId;
    private String choice;
    private String scope;
    private LocalDateTime occurredAt;
    private LocalDateTime receivedAt;
    private String countryCode;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getVisitorId() {
        return visitorId;
    }

    public void setVisitorId(String visitorId) {
        this.visitorId = visitorId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getChoice() {
        return choice;
    }

    public void setChoice(String choice) {
        this.choice = choice;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }
}
