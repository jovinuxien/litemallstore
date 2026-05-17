package org.linlinjava.litemall.promotion.utils;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class UserContext {

    public static final String CORRELATION_ID = "tmx-correlation-id";
    public static final String AUTH_TOKEN = "Authorization";
    public static final String USER_ID = "tmx-user-id";
    public static final String PROMOTION_SERVICE_ID = "tmx-promotion-service-id";

    private static final ThreadLocal<String> correlationId = new ThreadLocal<>();
    private static final ThreadLocal<String> authToken = new ThreadLocal<>();
    private static final ThreadLocal<String> userId = new ThreadLocal<>();
    private static final ThreadLocal<String> promotionServiceId = new ThreadLocal<>();

    public static String getCorrelationId() { return correlationId.get(); }
    public static void setCorrelationId(String cid) { correlationId.set(cid); }

    public static String getAuthToken() { return authToken.get(); }
    public static void setAuthToken(String aToken) { authToken.set(aToken); }

    public static String getUserId() { return userId.get(); }
    public static void setUserId(String aUser) { userId.set(aUser); }

    public static String getPromotionServiceId() { return promotionServiceId.get(); }
    public static void setPromotionServiceId(String aPromotion) { promotionServiceId.set(aPromotion); }

    public static HttpHeaders getHttpHeaders() {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set(CORRELATION_ID, getCorrelationId());
        return httpHeaders;
    }
}