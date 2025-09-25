package org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice;


import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.authentication.CJAuthenticationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenManager {

    private static final Logger logger = LoggerFactory.getLogger(TokenManager.class);
    private final ConcurrentHashMap<String, CJAuthenticationResponse> tokenStore = new ConcurrentHashMap<>();


    public String getAccessToken(String email) {
        CJAuthenticationResponse response = tokenStore.get(email);
        logger.debug("Retrieved token for {}: {}", email, response.getData().getAccessToken());
        return (response != null && response.getData() != null)? response.getData().getAccessToken() : null;
    }

    public CJAuthenticationResponse getResponseAuthAccessData(String email){
        return tokenStore.get(email);
    }

    public void storeToken(String email, CJAuthenticationResponse tokenResponse){
        logger.debug("Storing token for {}: {}", email, tokenResponse.getData().getAccessToken());
        tokenStore.put(email, tokenResponse);
    }

}
