package org.linlinjava.litemall.gatewayapi.auth;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Customer account self-service: register, password change/reset, profile.
 *
 * <p>Blocking MyBatis — the reactive controller runs every call on
 * boundedElastic. Failures are typed as {@link AccountException} carrying the
 * litemall errno the SPA switches on:
 *
 * <ul>
 *   <li>700 — wrong old password on change</li>
 *   <li>701 — email reset flow disabled (SPA hides the "forgot" tab)</li>
 *   <li>703 — invalid/expired/used reset token</li>
 *   <li>704 — username already registered</li>
 *   <li>705 — mobile already registered ({@code ''} treated as absent)</li>
 *   <li>402 — request/policy violation (litemall bad-argument convention)</li>
 *   <li>501 — not logged in / account gone (litemall unlogin convention)</li>
 * </ul>
 */
@Service
public class AccountService {

    public static final int ERR_BAD_ARGUMENT = 402;
    public static final int ERR_UNLOGIN = 501;
    public static final int ERR_WRONG_OLD_PASSWORD = 700;
    public static final int ERR_RESET_MAIL_DISABLED = 701;
    public static final int ERR_INVALID_RESET_TOKEN = 703;
    public static final int ERR_USERNAME_TAKEN = 704;
    public static final int ERR_MOBILE_TAKEN = 705;

    /** BCrypt truncates beyond 72 bytes; below 8 is policy-rejected. */
    private static final int PASSWORD_MIN_BYTES = 8;
    private static final int PASSWORD_MAX_BYTES = 72;

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final LitemallUserService userService;
    private final BCryptPasswordEncoder encoder;
    private final RefreshTokenService refreshTokens;
    private final ResetTokenService resetTokens;
    private final ResetMailSender resetMail;
    private final boolean resetMailEnabled;

    public AccountService(LitemallUserService userService,
                          BCryptPasswordEncoder passwordEncoder,
                          RefreshTokenService refreshTokens,
                          ResetTokenService resetTokens,
                          ResetMailSender resetMail,
                          @Value("${litemall.auth.reset-mail.enabled:false}") boolean resetMailEnabled) {
        this.userService = userService;
        this.encoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.resetMail = resetMail;
        this.resetMailEnabled = resetMailEnabled;
    }

    public boolean isResetMailEnabled() {
        return resetMailEnabled;
    }

    /**
     * Register a new customer account.
     *
     * <p>Duplicate username → 704 (pre-check for the friendly path; the
     * username unique index + {@link DuplicateKeyException} mapping closes the
     * race). Duplicate non-empty mobile → 705.
     */
    public LitemallUser register(String username, String password, String nickname,
                                 String email, String mobile) {
        username = trimToNull(username);
        if (username == null) {
            throw new AccountException(ERR_BAD_ARGUMENT, "username is required");
        }
        if (username.length() > 63) {
            throw new AccountException(ERR_BAD_ARGUMENT, "username must be at most 63 characters");
        }
        checkPasswordPolicy(password, username);
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null && email != null && !email.isBlank()) {
            throw new AccountException(ERR_BAD_ARGUMENT, "invalid email address");
        }
        email = normalizedEmail;
        mobile = trimToNull(mobile);

        if (userService.checkByUsername(username)) {
            throw new AccountException(ERR_USERNAME_TAKEN, "username already registered");
        }
        if (mobile != null && !userService.queryByMobile(mobile).isEmpty()) {
            throw new AccountException(ERR_MOBILE_TAKEN, "mobile already registered");
        }

        LitemallUser user = new LitemallUser();
        user.setUsername(username);
        user.setPassword(encoder.encode(password));
        user.setNickname(nickname == null || nickname.isBlank() ? username : nickname.trim());
        user.setEmail(email);
        // NOT NULL columns without a usable default:
        user.setMobile(mobile == null ? "" : mobile);
        user.setAvatar("");
        user.setWeixinOpenid("");
        user.setSessionKey("");
        user.setLastLoginIp("");
        user.setGender((byte) 0);
        user.setUserLevel((byte) 0);
        user.setStatus((byte) 0);
        user.setLastLoginTime(LocalDateTime.now());
        user.setDeleted(false);
        try {
            userService.add(user);
        } catch (DuplicateKeyException e) {
            // Concurrent register with the same username lost the race.
            throw new AccountException(ERR_USERNAME_TAKEN, "username already registered");
        }
        return user;
    }

    /** Authenticated password change; wrong old password → 700. */
    public void changePassword(Integer userId, String oldPassword, String newPassword) {
        LitemallUser user = requireUser(userId);
        if (oldPassword == null || !encoder.matches(oldPassword, user.getPassword())) {
            throw new AccountException(ERR_WRONG_OLD_PASSWORD, "old password is incorrect");
        }
        checkPasswordPolicy(newPassword, user.getUsername());
        LitemallUser patch = new LitemallUser();
        patch.setId(user.getId());
        patch.setPassword(encoder.encode(newPassword));
        userService.updateById(patch);
        refreshTokens.revokeAllForUser(user.getId());
    }

    /**
     * Forgot-password request. Disabled → 701. Enabled: ALWAYS succeeds from
     * the caller's perspective (anti-enumeration) — a token is issued and the
     * send fired only when the email maps to exactly one live account.
     */
    public void requestReset(String email) {
        if (!resetMailEnabled) {
            throw new AccountException(ERR_RESET_MAIL_DISABLED, "password reset by email is not available");
        }
        email = normalizeEmail(email);
        if (email == null) {
            throw new AccountException(ERR_BAD_ARGUMENT, "a valid email is required");
        }
        List<LitemallUser> matches = userService.queryByEmail(email);
        if (matches.size() == 1) {
            String raw = resetTokens.issue(matches.get(0).getId());
            String to = email;
            try {
                resetMail.send(to, raw);
            } catch (RuntimeException e) {
                // Fire-and-forget: delivery failure never surfaces to the caller.
                log.warn("reset-mail send failed for {}", to, e);
            }
        }
        // Zero or ambiguous matches: silently succeed (anti-enumeration).
    }

    /** Forgot-password confirm. Disabled → 701; bad/used/expired token → 703. */
    public void confirmReset(String token, String newPassword) {
        if (!resetMailEnabled) {
            throw new AccountException(ERR_RESET_MAIL_DISABLED, "password reset by email is not available");
        }
        Integer userId;
        try {
            userId = resetTokens.consume(token);
        } catch (ResetTokenService.InvalidResetTokenException e) {
            throw new AccountException(ERR_INVALID_RESET_TOKEN, "invalid or expired reset token");
        }
        LitemallUser user = requireUser(userId);
        checkPasswordPolicy(newPassword, user.getUsername());
        LitemallUser patch = new LitemallUser();
        patch.setId(user.getId());
        patch.setPassword(encoder.encode(newPassword));
        userService.updateById(patch);
        resetTokens.invalidateAllForUser(user.getId());
        refreshTokens.revokeAllForUser(user.getId());
    }

    /** Partial profile update; only supplied fields change. Mobile dedupe → 705. */
    public LitemallUser updateProfile(Integer userId, String nickname, String email,
                                      String mobile, String avatar, Byte gender, String birthday) {
        LitemallUser user = requireUser(userId);
        LitemallUser patch = new LitemallUser();
        patch.setId(user.getId());
        boolean dirty = false;
        if (nickname != null && !nickname.isBlank()) {
            patch.setNickname(nickname.trim());
            dirty = true;
        }
        if (email != null) {
            // '' clears is NOT supported (updateByPrimaryKeySelective skips null);
            // a non-empty value must be valid.
            String normalized = normalizeEmail(email);
            if (normalized == null && !email.isBlank()) {
                throw new AccountException(ERR_BAD_ARGUMENT, "invalid email address");
            }
            if (normalized != null) {
                patch.setEmail(normalized);
                dirty = true;
            }
        }
        if (mobile != null && !mobile.isBlank()) {
            String m = mobile.trim();
            boolean takenByOther = userService.queryByMobile(m).stream()
                    .anyMatch(u -> !u.getId().equals(user.getId()));
            if (takenByOther) {
                throw new AccountException(ERR_MOBILE_TAKEN, "mobile already registered");
            }
            patch.setMobile(m);
            dirty = true;
        }
        if (avatar != null && !avatar.isBlank()) {
            patch.setAvatar(avatar.trim());
            dirty = true;
        }
        if (gender != null) {
            patch.setGender(gender);
            dirty = true;
        }
        if (birthday != null && !birthday.isBlank()) {
            try {
                patch.setBirthday(LocalDate.parse(birthday.trim()));
            } catch (DateTimeParseException e) {
                throw new AccountException(ERR_BAD_ARGUMENT, "birthday must be an ISO date (yyyy-MM-dd)");
            }
            dirty = true;
        }
        if (dirty) {
            userService.updateById(patch);
        }
        return requireUser(userId);
    }

    /** Load the live account behind a token; deleted/missing → 501 unlogin. */
    public LitemallUser requireUser(Integer userId) {
        if (userId == null) {
            throw new AccountException(ERR_UNLOGIN, "please login");
        }
        LitemallUser user = userService.findById(userId);
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            throw new AccountException(ERR_UNLOGIN, "please login");
        }
        return user;
    }

    private void checkPasswordPolicy(String password, String username) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length < PASSWORD_MIN_BYTES) {
            throw new AccountException(ERR_BAD_ARGUMENT, "password must be at least 8 characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw new AccountException(ERR_BAD_ARGUMENT, "password must be at most 72 bytes");
        }
        if (password.equals(username)) {
            throw new AccountException(ERR_BAD_ARGUMENT, "password must differ from username");
        }
    }

    /** @return the trimmed lower-cased email, or null when absent/blank/invalid. */
    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String e = email.trim().toLowerCase();
        if (e.length() > 127 || !EMAIL.matcher(e).matches()) {
            return null;
        }
        return e;
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** Account-flow failure carrying the litemall errno for the envelope. */
    public static class AccountException extends RuntimeException {
        private final int errno;

        public AccountException(int errno, String message) {
            super(message);
            this.errno = errno;
        }

        public int getErrno() {
            return errno;
        }
    }
}
