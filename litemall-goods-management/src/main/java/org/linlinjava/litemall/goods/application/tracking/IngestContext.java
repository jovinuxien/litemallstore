package org.linlinjava.litemall.goods.application.tracking;

/**
 * Request-scoped facts the controller derives ONCE per batch and the ingest
 * service stamps onto every event: edge-owned identity (headers, never body),
 * the edge-verified user id, and the derived-then-discarded geo/device/locale.
 */
public class IngestContext {

    private final String visitorId;
    private final String sessionId;
    private final Integer userId;
    private final String countryCode;
    private final String deviceType;
    private final String locale;

    public IngestContext(String visitorId, String sessionId, Integer userId,
                         String countryCode, String deviceType, String locale) {
        this.visitorId = visitorId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.countryCode = countryCode;
        this.deviceType = deviceType;
        this.locale = locale;
    }

    public String getVisitorId() {
        return visitorId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public String getLocale() {
        return locale;
    }
}
