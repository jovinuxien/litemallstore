package org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.authentication.CJAuthenticationClient;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.authentication.CJAuthenticationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CJAuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(CJAuthenticationService.class);


    @Autowired
    private CJAuthenticationClient authenticationClient;

    @Autowired
    private TokenManager tokenManager;

    @Autowired
    private CJAuthenticationResponse cachedResponse;

    private Map<String, CJAuthenticationResponse> tokenCache = new ConcurrentHashMap<>();




    /**
     * Authenticates the user and stores the access token and refresh token.
     * If the access token is expired, it uses the refresh token to get a new access token.
     */
    public void accessTokenFromAuthentication(String email, String cjApiKey) {
        try {
            CJAuthenticationResponse cachedResponse = tokenCache.get(email);

            // If we have a valid cached token, use it
            if (cachedResponse != null && !isTokenExpired(cachedResponse)) {
                logger.debug("Using cached token for email: {}", email);
                return;
            }
            // If we have a refresh token and the access token is expired, try to refresh
            if (cachedResponse != null &&
                    cachedResponse.getData() != null &&
                    cachedResponse.getData().getRefreshToken() != null) {

                try {
                    CJAuthenticationResponse refreshedResponse = authenticationClient.refreshToken(
                            cachedResponse.getData().getRefreshToken());

                    if (refreshedResponse != null && refreshedResponse.getData() != null) {
                        tokenManager.storeToken(email, refreshedResponse);
                        tokenCache.put(email, refreshedResponse);
                        logger.info("Successfully refreshed token for email: {}", email);
                        return;
                    }
                } catch (Exception e) {
                    logger.warn("Refresh token failed for email: {}, falling back to new authentication. Error: {}",
                            email, e.getMessage());
                }
            }

            // Full authentication if no valid cached token or refresh failed
            CJAuthenticationResponse newResponse = authenticationClient.accessToken(email, cjApiKey);
            if (newResponse != null && newResponse.getData() != null) {
                tokenManager.storeToken(email, newResponse);
                tokenCache.put(email, newResponse);
                logger.info("Successfully authenticated and cached token for email: {}, {}", email, tokenManager.getAccessToken(email));
            } else {
                throw new RuntimeException("Authentication failed - no valid response");
            }
        } catch (Exception e) {
            logger.error("Authentication error for email: {}", email, e);
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        }

    }


    public String getAccessTokenFromManager(String email){
        if(cachedResponse == null || isTokenExpired(cachedResponse)){
          throw new RuntimeException("Access token is not available. Please authenticate first");
        }
        return tokenManager.getAccessToken(email);
    }

    public CJAuthenticationResponse getResponseAuthAccessData(String email){
        cachedResponse = tokenManager.getResponseAuthAccessData(email);

        if(cachedResponse == null || isTokenExpired(cachedResponse)){
            throw new RuntimeException("Access token is not available. Please Authenticate first");
        }
        return cachedResponse;
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(CJAuthenticationResponse response) {
        if (response == null || response.getData() == null) {
            return true;
        }

        // Assuming the response contains expiration time in seconds
        long expiresAt = response.getData().getAccessTokenExpiryDate().toEpochSecond(); // Unix timestamp
        long bufferTime = 60; // 1 minute buffer
        return System.currentTimeMillis() / 1000 >= (expiresAt - bufferTime);
    }
}
