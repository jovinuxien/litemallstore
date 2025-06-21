package org.linlinjava.litemall.admin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;

import java.util.UUID;


public class TokenService {


    private static final Logger LOGGER = LoggerFactory.getLogger(TokenService.class);
    private static final int HALF_AN_HOUR_IN_MILLISECONDS = 30 * 60 * 1000;

    public String generateNewToken() {
        return UUID.randomUUID().toString();
    }

    @CachePut(value = "restApiAuthTokenCache", key = "#token")
    public Authentication store(String token, Authentication authentication) {
        return authentication;
    }

    @Cacheable(value = "restApiAuthTokenCache", key = "#token"  )
    public Authentication retrieve(String token) {
        return  null; // called when if token is not in cache
    }
    public boolean contains(String token) {
        return retrieve(token) != null;
    }

    @CacheEvict(value = "restApiAuthTokenCache", allEntries = true)
    @Scheduled(fixedRate = HALF_AN_HOUR_IN_MILLISECONDS)
    public void evictAllTokens() {
        LOGGER.info("Evicting all tokens from cache");
    }

    @CacheEvict(value = "restApiAuthTokenCache", key = "#token")
    public void evictToken(String token) {
        LOGGER.info("Evicting token: {}", token);
    }
}
