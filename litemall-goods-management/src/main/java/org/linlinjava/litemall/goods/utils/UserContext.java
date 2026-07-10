package org.linlinjava.litemall.goods.utils;


import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Request-scoped (ThreadLocal) carrier for the trusted-identity headers
 * forwarded by litemall-gateway-admin's {@code IdentityForwardingFilter}
 * after a successful Bearer-token validation. Downstream services trust
 * {@code X-User-Id} / {@code X-User-Type} / {@code X-User-Roles} only because
 * the edge stripped any client-supplied {@code X-User-*} headers first.
 */
@Component
public class UserContext {
    public static final String HDR_USER_ID = "X-User-Id";
    public static final String HDR_USER_TYPE = "X-User-Type";
    public static final String HDR_USER_ROLES = "X-User-Roles";
    public static final String HDR_CORRELATION_ID = "tmx-correlation-id";
    public static final String HDR_AUTH_TOKEN = "Authorization";

    private static final ThreadLocal<String> userId = new ThreadLocal<>();
    private static final ThreadLocal<String> userType = new ThreadLocal<>();
    private static final ThreadLocal<String> userRoles = new ThreadLocal<>();
    private static final ThreadLocal<String> correlationId = new ThreadLocal<>();
    private static final ThreadLocal<String> authToken = new ThreadLocal<>();

    public static String getUserId() { return userId.get(); }
    public static void setUserId(String v) { userId.set(v); }

    /** The forwarded user id as an int, or null when anonymous/malformed. */
    public static Integer getUserIdAsInt() {
        String raw = getUserId();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String getUserType() { return userType.get(); }
    public static void setUserType(String v) { userType.set(v); }

    public static String getUserRoles() { return userRoles.get(); }
    public static void setUserRoles(String v) { userRoles.set(v); }

    public static String getCorrelationId() { return correlationId.get(); }
    public static void setCorrelationId(String v) { correlationId.set(v); }

    public static String getAuthToken() { return authToken.get(); }
    public static void setAuthToken(String v) { authToken.set(v); }

    public static void clear() {
        userId.remove();
        userType.remove();
        userRoles.remove();
        correlationId.remove();
        authToken.remove();
    }

    public static HttpHeaders getHttpHeaders() {
        HttpHeaders h = new HttpHeaders();
        if (getCorrelationId() != null) h.set(HDR_CORRELATION_ID, getCorrelationId());
        if (getUserId() != null) h.set(HDR_USER_ID, getUserId());
        if (getUserType() != null) h.set(HDR_USER_TYPE, getUserType());
        if (getUserRoles() != null) h.set(HDR_USER_ROLES, getUserRoles());
        return h;
    }
}
