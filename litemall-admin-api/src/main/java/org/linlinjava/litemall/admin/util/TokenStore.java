package org.linlinjava.litemall.admin.util;

import org.springframework.security.core.userdetails.UserDetails;

public interface TokenStore {
    boolean isValidToken(String token);
    String getUsernameFromToken(String token);
    String createToken(UserDetails userDetails);
    void invalidateToken(String token);
}
