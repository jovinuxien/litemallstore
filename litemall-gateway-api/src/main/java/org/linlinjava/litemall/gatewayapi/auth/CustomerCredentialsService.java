package org.linlinjava.litemall.gatewayapi.auth;

import java.util.List;

import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Verifies customer credentials directly against the litemall user store.
 *
 * <p>Mirrors the BCrypt check in {@code WxAuthController#login} so the customer
 * edge is self-contained (no runtime auth dependency on wx-api). Blocking
 * MyBatis — callers run it on a boundedElastic scheduler.
 */
@Service
public class CustomerCredentialsService {

    private final LitemallUserService userService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public CustomerCredentialsService(LitemallUserService userService) {
        this.userService = userService;
    }

    /**
     * @return the authenticated user
     * @throws BadCredentialsException if the account is missing/ambiguous or the
     *                                 password does not match
     */
    public LitemallUser authenticate(String username, String password) {
        if (username == null || password == null) {
            throw new BadCredentialsException("username and password are required");
        }
        List<LitemallUser> users = userService.queryByUsername(username);
        if (users.size() != 1) {
            throw new BadCredentialsException("account not found");
        }
        LitemallUser user = users.get(0);
        if (!encoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("invalid username or password");
        }
        return user;
    }

    /** Invalid customer credentials at /auth/login. */
    public static class BadCredentialsException extends RuntimeException {
        public BadCredentialsException(String message) {
            super(message);
        }
    }
}
