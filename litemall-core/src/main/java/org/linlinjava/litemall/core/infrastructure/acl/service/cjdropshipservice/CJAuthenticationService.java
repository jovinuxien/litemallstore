package org.linlinjava.litemall.core.infrastructure.acl.service.cjdropshipservice;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.core.infrastructure.acl.client.cjdropshipclient.authentication.CJAuthenticationClient;
import org.linlinjava.litemall.core.infrastructure.acl.dto.cjdropshipdto.authentication.CJAuthenticationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;

@Service
public class CJAuthenticationService {

    @Autowired
    private CJAuthenticationClient authenticationClient;

    @Autowired
    private TokenManager tokenManager;

    private CJAuthenticationResponse cachedResponse;



    /**
     * Authenticates the user and stores the access token and refresh token.
     * If the access token is expired, it uses the refresh token to get a new access token.
     */
    public void accessToken(String email, String cjAPiKey) {
       /* if (cachedResponse == null || isAccessTokenExpired()) {
            // First-time authentication or access token expired
            if (cachedResponse == null || cachedResponse.getData().getRefreshToken() == null) {
                // No refresh token available, authenticate using email and password
                CJAuthenticationResponse response = authenticationClient.accessToken(email, cjAPiKey);
                tokenManager.storeToken(email, response);
                cachedResponse = response;
            } else {
                // Use refresh token to get a new access token
                CJAuthenticationResponse response = authenticationClient.refreshToken(cachedResponse.getData().getRefreshToken());
                tokenManager.storeToken(email, response);
                cachedResponse = response;
            }
        }*/

        try {
            if (cachedResponse == null || isAccessTokenExpired()) {
                if (cachedResponse == null || cachedResponse.getData().getRefreshToken() == null) {
                    // First-time authentication
                    CJAuthenticationResponse response = authenticationClient.accessToken(email, cjAPiKey);
                    tokenManager.storeToken(email, response);
                    cachedResponse = response;
                    System.out.println("Access token stored for email: " + email + " is " + response);
                } else {
                    // Refresh token
                    CJAuthenticationResponse response = authenticationClient.refreshToken(cachedResponse.getData().getRefreshToken());
                    tokenManager.storeToken(email, response);
                    cachedResponse = response;
                    System.out.println("Access token refreshed for email: " + email + " is " + response);
                }
            }
        } catch (Exception e) {
            System.err.println("Error in accessToken: " + e.getMessage());
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        }
    }


    public String getAccessToken(String email){
        if(cachedResponse == null || isAccessTokenExpired()){
          throw new RuntimeException("Access token is not available. Please authenticate first");
        }
        return tokenManager.getAccessToken(email);
    }




    public CJAuthenticationResponse getResponseAuthAccessData(String email){
        cachedResponse = tokenManager.getResponseAuthAccessData(email);

        if(cachedResponse == null || isAccessTokenExpired()){
            throw new RuntimeException("Access token is not available. Please Authenticate first");
        }
        return cachedResponse;
    }


    private boolean isAccessTokenExpired(){
        if(cachedResponse == null || cachedResponse.getData() == null){
            return true; // No token available. considered expired;
        }
        OffsetDateTime expiryDate = cachedResponse.getData().getAccessTokenExpiryDate();
       return OffsetDateTime.now().isAfter(expiryDate);
    }
}
