package org.linlinjava.litemall.gatewayadmin.auth;

import java.util.List;

import org.linlinjava.litemall.db.domain.LitemallAdmin;
import org.linlinjava.litemall.db.service.LitemallAdminService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Verifies admin credentials directly against the litemall admin store.
 *
 * <p>Mirrors the BCrypt check in litemall-admin-api ({@code findAdmin} +
 * {@code BCryptPasswordEncoder.matches}) so the admin edge is self-contained
 * (no runtime auth dependency on admin-api). Blocking MyBatis — callers run
 * it on a boundedElastic scheduler.
 */
@Service
public class AdminCredentialsService {

    private final LitemallAdminService adminService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AdminCredentialsService(LitemallAdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * @return the authenticated admin
     * @throws BadCredentialsException if the account is missing/ambiguous or the
     *                                 password does not match
     */
    public LitemallAdmin authenticate(String username, String password) {
        if (username == null || password == null) {
            throw new BadCredentialsException("username and password are required");
        }
        List<LitemallAdmin> admins = adminService.findAdmin(username);
        if (admins.size() != 1) {
            throw new BadCredentialsException("account not found");
        }
        LitemallAdmin admin = admins.get(0);
        if (!encoder.matches(password, admin.getPassword())) {
            throw new BadCredentialsException("invalid username or password");
        }
        return admin;
    }

    /** Invalid admin credentials at /auth/login. */
    public static class BadCredentialsException extends RuntimeException {
        public BadCredentialsException(String message) {
            super(message);
        }
    }
}
