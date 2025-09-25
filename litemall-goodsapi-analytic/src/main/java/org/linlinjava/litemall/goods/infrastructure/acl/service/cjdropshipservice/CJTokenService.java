package org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice;


import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.authentication.CJAuthenticationResponse;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.springframework.stereotype.Service;

@Service
public class CJTokenService {
    private final CJAuthenticationService authService;
    private final CJDropshippingConfig config;
    private final Object tokenLock = new Object();
    private String cachedToken;
    private long tokenExpiry;

    public CJTokenService(CJAuthenticationService authService, CJDropshippingConfig config) {
        this.authService = authService;
        this.config = config;
    }

    public String getValidToken() {
        // Fast path - return valid cached token
        if (cachedToken != null && !isTokenExpired()) {
            return cachedToken;
        }
        // Synchronized refresh
        synchronized (tokenLock) {
            // Double-check after lock
            if (cachedToken != null && !isTokenExpired()) {
                return cachedToken;
            }
            refreshToken();
            return cachedToken;
        }
    }
    private void refreshToken() {
        authService.accessTokenFromAuthentication(
                config.getCjEmail(),
                config.getCjApiKey()
        );

        CJAuthenticationResponse response = authService.getResponseAuthAccessData(config.getCjEmail());
        if (response == null || response.getData() == null) {
            throw new RuntimeException("Authentication failed - no token received");
        }

        this.cachedToken = response.getData().getAccessToken();
        this.tokenExpiry = System.currentTimeMillis() +
                (response.getData().getAccessTokenExpiryDate().toEpochSecond() * 1000); // Convert seconds to ms
    }

    private boolean isTokenExpired() {
        return System.currentTimeMillis() >= tokenExpiry;
    }
}
