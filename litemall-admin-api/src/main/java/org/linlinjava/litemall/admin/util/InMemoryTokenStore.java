package org.linlinjava.litemall.admin.util;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class InMemoryTokenStore implements TokenStore  {

    private Map<String, String> usernameToToken = new ConcurrentHashMap<>();
    private Map<String, String> tokenToUsername = new ConcurrentHashMap<>();

    private final long tokenValidity = Duration.ofHours(6).toMillis(); // 1 hour

    @Override
    public boolean isValidToken(String token) {
        return tokenToUsername.containsKey(token);
    }

    @Override
    public String getUsernameFromToken(String token) {
        return tokenToUsername.get(token);
    }

    @Override
    public String createToken(UserDetails userDetails) {

        String  token = UUID.randomUUID().toString();
        String username = userDetails.getUsername();
        // Invalidate existing token for the same user
        if(usernameToToken.containsKey(username)) {
           tokenToUsername.remove(usernameToToken.get(username));
        }

        tokenToUsername.put(token, username);
        usernameToToken.put(username, token);

        // Schedule token invalidation
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                invalidateToken(token);
            }
        }, tokenValidity);
        return token;
    }

    @Override
    public void invalidateToken(String token) {
        String username = tokenToUsername.remove(token);
        if (username != null) {
            usernameToToken.remove(username);
        }
    }
}
