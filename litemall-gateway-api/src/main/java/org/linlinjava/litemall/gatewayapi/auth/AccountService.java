package org.linlinjava.litemall.gatewayapi.auth;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
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
    /** Wave 16: guest provisioning refused — the email already has a real account. */
    public static final int ERR_EMAIL_HAS_ACCOUNT = 706;
    /** Wave 16: Google Sign-In not configured on this deployment. */
    public static final int ERR_GOOGLE_DISABLED = 707;
    /** Wave 16: Google credential failed verification. */
    public static final int ERR_GOOGLE_INVALID = 708;

    /** BCrypt truncates beyond 72 bytes; below 8 is policy-rejected. */
    private static final int PASSWORD_MIN_BYTES = 8;
    private static final int PASSWORD_MAX_BYTES = 72;

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** Wave-5 invite code: {@code "A" + base36(uid)} uppercase (shared contract). */
    private static final Pattern INVITE_CODE = Pattern.compile("^A([0-9A-Z]{1,12})$");

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
     *
     * <p>Wave-5: an optional invite code binds the new account to a live
     * promoter (permanent {@code spread_uid}). An invite problem NEVER fails or
     * delays registration and surfaces no error — the account just registers
     * with {@code spread_uid = 0}.
     */
    public LitemallUser register(String username, String password, String nickname,
                                 String email, String mobile, String inviteCode) {
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
        bindInvite(user, inviteCode);
        return user;
    }

    /**
     * Best-effort invite binding (Wave-5): decode {@code "A" + base36(uid)};
     * the referrer must exist, be a promoter, and not be deleted. Valid ⇒
     * permanent {@code spread_uid}/{@code spread_time}/{@code path} on the new
     * account + referrer {@code spread_count} +1. Anything else — absent,
     * malformed, unknown, or non-promoter code, or a lost race — is silently
     * ignored: the account stays registered with {@code spread_uid = 0}.
     */
    private void bindInvite(LitemallUser user, String inviteCode) {
        try {
            Integer referrerId = decodeInviteCode(inviteCode);
            if (referrerId == null || referrerId.equals(user.getId())) {
                return;
            }
            LitemallUser referrer = userService.findById(referrerId);
            if (referrer == null || Boolean.TRUE.equals(referrer.getDeleted())
                    || !Boolean.TRUE.equals(referrer.getIsPromoter())) {
                return;
            }
            String path = (referrer.getPath() == null || referrer.getPath().isBlank()
                    ? "/0/" : referrer.getPath()) + referrer.getId() + "/";
            if (userService.bindSpread(user.getId(), referrer.getId(), path)) {
                userService.incrementSpreadCount(referrer.getId());
            }
        } catch (RuntimeException e) {
            // Invite binding must never break registration.
            log.warn("invite binding skipped for new user {}", user.getId(), e);
        }
    }

    /** @return the referrer uid, or null when the code is absent/malformed. */
    private Integer decodeInviteCode(String code) {
        if (code == null) {
            return null;
        }
        Matcher m = INVITE_CODE.matcher(code.trim().toUpperCase());
        if (!m.matches()) {
            return null;
        }
        long uid = Long.parseLong(m.group(1), 36);
        return uid <= 0 || uid > Integer.MAX_VALUE ? null : (int) uid;
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

    /**
     * Wave 16: guest-checkout shadow account. A password-less account keyed to
     * the (normalized) email so the order attaches to a real user id and the
     * mail pipeline has a recipient. An email that already belongs to a REAL
     * (non-guest) account → 706, the SPA prompts sign-in instead — a guest
     * flow must never become a way to act as someone else's account.
     *
     * <p>Every guest checkout gets a FRESH shadow account, even for a repeated
     * email — never a session over an earlier guest's orders and address:
     * knowing an email must grant nothing. Claiming (password or Google) binds
     * history forward from the session that actually placed the order.
     *
     * <p>The stored password is the empty string: BCrypt's matcher can never
     * accept it, so the account is unloginable by password until claimed.
     */
    public LitemallUser provisionGuest(String email) {
        String normalized = normalizeEmail(email);
        if (normalized == null) {
            throw new AccountException(ERR_BAD_ARGUMENT, "a valid email is required");
        }
        boolean hasRealAccount = userService.queryByEmail(normalized).stream()
                .anyMatch(u -> !Boolean.TRUE.equals(u.getIsGuest()));
        if (hasRealAccount) {
            throw new AccountException(ERR_EMAIL_HAS_ACCOUNT, "this email already has an account — please sign in");
        }
        LitemallUser user = newBlankUser(normalized);
        user.setIsGuest(true);
        try {
            userService.add(user);
        } catch (DuplicateKeyException e) {
            // Username race — retry once with a random guest handle.
            user = newBlankUser(normalized);
            user.setUsername("guest-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            user.setIsGuest(true);
            userService.add(user);
        }
        return user;
    }

    /**
     * Wave 16: an authenticated guest claims the account by setting a password.
     * Refresh tokens are NOT revoked — the claimer is the current session.
     */
    public LitemallUser claimGuest(Integer userId, String password) {
        LitemallUser user = requireUser(userId);
        if (!Boolean.TRUE.equals(user.getIsGuest())) {
            throw new AccountException(ERR_BAD_ARGUMENT, "this account is not a guest account");
        }
        checkPasswordPolicy(password, user.getUsername());
        LitemallUser patch = new LitemallUser();
        patch.setId(user.getId());
        patch.setPassword(encoder.encode(password));
        patch.setIsGuest(false);
        userService.updateById(patch);
        return requireUser(userId);
    }

    /**
     * Wave 16: Google Sign-In on a VERIFIED identity (the caller has already
     * validated the ID token — signature/audience/issuer/email_verified).
     * Precedence: existing link by {@code google_sub} (Google's durable key —
     * emails can change on their side) → link by email (a guest account is
     * upgraded, a password account is linked; either way the owner proved
     * control of the mailbox to Google) → fresh account.
     */
    public LitemallUser googleSignIn(GoogleIdentity identity) {
        List<LitemallUser> bySub = userService.queryByGoogleSub(identity.sub());
        if (bySub.size() == 1) {
            return bySub.get(0);
        }
        String email = normalizeEmail(identity.email());
        if (email == null) {
            throw new AccountException(ERR_GOOGLE_INVALID, "Google account has no usable email");
        }
        // Linking targets: the email's REAL account first; else a single guest
        // shadow account (upgraded — Google verified the mailbox). Several
        // guest rows for one email (repeat guest buyer) are ambiguous — skip
        // linking and open a fresh account rather than guess.
        List<LitemallUser> byEmail = userService.queryByEmail(email);
        List<LitemallUser> real = byEmail.stream().filter(u -> !Boolean.TRUE.equals(u.getIsGuest())).toList();
        if (real.size() > 1) {
            throw new AccountException(ERR_EMAIL_HAS_ACCOUNT, "this email is ambiguous — please sign in with your password");
        }
        LitemallUser linkTarget = real.size() == 1 ? real.get(0)
                : byEmail.size() == 1 ? byEmail.get(0) : null;
        if (linkTarget != null) {
            LitemallUser user = linkTarget;
            LitemallUser patch = new LitemallUser();
            patch.setId(user.getId());
            patch.setGoogleSub(identity.sub());
            if (Boolean.TRUE.equals(user.getIsGuest())) {
                patch.setIsGuest(false); // Google verified the mailbox — the shadow account is claimed
            }
            if ((user.getAvatar() == null || user.getAvatar().isBlank()) && identity.picture() != null) {
                patch.setAvatar(identity.picture());
            }
            try {
                userService.updateById(patch);
            } catch (DuplicateKeyException e) {
                // google_sub raced onto another row; that row wins.
                List<LitemallUser> again = userService.queryByGoogleSub(identity.sub());
                if (again.size() == 1) {
                    return again.get(0);
                }
                throw new AccountException(ERR_GOOGLE_INVALID, "could not link Google account");
            }
            return requireUser(user.getId());
        }
        LitemallUser user = newBlankUser(email);
        user.setGoogleSub(identity.sub());
        user.setIsGuest(false);
        if (identity.name() != null && !identity.name().isBlank()) {
            user.setNickname(identity.name().trim());
        }
        if (identity.picture() != null && !identity.picture().isBlank()) {
            user.setAvatar(identity.picture().trim());
        }
        try {
            userService.add(user);
        } catch (DuplicateKeyException e) {
            List<LitemallUser> again = userService.queryByGoogleSub(identity.sub());
            if (again.size() == 1) {
                return again.get(0);
            }
            throw new AccountException(ERR_GOOGLE_INVALID, "could not create the Google-linked account");
        }
        return user;
    }

    /** Verified facts from a Google ID token (see GoogleTokenVerifier). */
    public record GoogleIdentity(String sub, String email, String name, String picture) {
    }

    /**
     * A fresh account row keyed to an email, with every NOT-NULL column filled
     * and an UNMATCHABLE password (empty string — BCrypt never accepts it).
     * Username: the email when it fits and is free, else a random guest handle;
     * usernames are display/login handles, the email is the durable key here.
     */
    private LitemallUser newBlankUser(String email) {
        String username = email.length() <= 63 && !userService.checkByUsername(email)
                ? email
                : "guest-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        LitemallUser user = new LitemallUser();
        user.setUsername(username);
        user.setPassword("");
        user.setNickname(email.substring(0, email.indexOf('@')));
        user.setEmail(email);
        user.setMobile("");
        user.setAvatar("");
        user.setWeixinOpenid("");
        user.setSessionKey("");
        user.setLastLoginIp("");
        user.setGender((byte) 0);
        user.setUserLevel((byte) 0);
        user.setStatus((byte) 0);
        user.setLastLoginTime(LocalDateTime.now());
        user.setDeleted(false);
        return user;
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
