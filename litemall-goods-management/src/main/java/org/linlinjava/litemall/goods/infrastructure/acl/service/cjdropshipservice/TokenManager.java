package org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice;


import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.authentication.CJAuthenticationResponse;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenManager {

    private final ConcurrentHashMap<String, CJAuthenticationResponse> tokenStore = new ConcurrentHashMap<>();


    /*public String getAccessToken(String email){
        CJAuthenticationResponse response = tokenStore.get(email);
        return response != null ? response.getData().getAccessToken() : null;
    }*/
    public String getAccessToken(String email) {
        CJAuthenticationResponse response = tokenStore.get(email);
        if (response == null) {
            System.err.println("No access token found for email: " + email);
            return null;
        }
        System.out.println("Access token retrieved for email: " + email);
        return response.getData().getAccessToken();
    }

    public CJAuthenticationResponse getResponseAuthAccessData(String email){
        return tokenStore.get(email);
    }

    public void storeToken(String email, CJAuthenticationResponse tokenResponse){
        tokenStore.put(email, tokenResponse);
        System.out.println("Access token stored  for email from tokenManager : " + email + " is " + tokenResponse.getData());

    }

}
