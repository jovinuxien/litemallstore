package org.linlinjava.litemall.wallet.utils;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class UserContext {

    public static final String CORRELATION_ID_HEADER = "correlation-id";
    public static final String AUTH_TOKEN = "Authorization";
    public static final String USER_ID = "userId";
    public static final String WALLET_SERVICE_ID = "tmx-wallet-service-id";

    private static final ThreadLocal<String> correlationId = new ThreadLocal<>();
    private static final ThreadLocal<String> authToken = new ThreadLocal<>();
    private static final ThreadLocal<String> userId = new ThreadLocal<>();
    private static final ThreadLocal<String> walletServiceId = new ThreadLocal<>();

    public static String getCorrelationId() {
        return correlationId.get();
    }

    public static void setCorrelationId(String cid) {
        correlationId.set(cid);
    }

    public static String getAuthToken() {
        return authToken.get();
    }

    public static void setAuthToken(String aToken) {
        authToken.set(aToken);
    }

    public static String getUserId() {
        return userId.get();
    }

    public static void setUserId(String aUser) {
        userId.set(aUser);
    }

    public static String getWalletServiceId() {
        return walletServiceId.get();
    }

    public static void setWalletServiceId(String aWallet) {
        walletServiceId.set(aWallet);
    }

    public static HttpHeaders getHttpHeaders() {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set(CORRELATION_ID_HEADER, getCorrelationId());
        return httpHeaders;
    }
}
