package org.linlinjava.litemall.gatewayapi.auth;

import java.util.HashMap;
import java.util.Map;

import org.linlinjava.litemall.db.auth.JwtService;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.gatewayapi.security.IdentityForwardingFilter;
import org.linlinjava.litemall.gatewayapi.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Customer authentication + account self-service at the edge (BFF).
 *
 * <p>Issues/validates a self-signed customer JWT here — never relayed
 * downstream, no Keycloak. Refresh tokens are DB-backed and rotating
 * (V15 table); password-reset tokens are single-use (V37 table). Credential
 * and DB work is blocking MyBatis, run on a boundedElastic scheduler so the
 * Netty event loop is never blocked. Responses use the litemall
 * {@code {errno,errmsg,data}} envelope.
 *
 * <p>Identity on authenticated handlers ({@code /auth/me|profile|reset}) comes
 * from the {@code X-User-Id} header: {@link IdentityForwardingFilter} strips
 * any client-supplied value and re-injects it only from a verified Bearer
 * token, so by the time a handler sees it, it is trusted.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String LOGIN_TYPE = "h5";

    private final CustomerCredentialsService credentials;
    private final RefreshTokenService refreshTokens;
    private final AccountService account;
    private final JwtService jwt;

    public AuthController(CustomerCredentialsService credentials,
                          RefreshTokenService refreshTokens,
                          AccountService account,
                          JwtService customerJwtService) {
        this.credentials = credentials;
        this.refreshTokens = refreshTokens;
        this.account = account;
        this.jwt = customerJwtService;
    }

    @PostMapping("/login")
    public Mono<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            LitemallUser user = credentials.authenticate(
                    body.get("username"), body.get("password"));
            return ApiResponse.ok(loginPayload(user));
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(CustomerCredentialsService.BadCredentialsException.class,
                        e -> Mono.just(ApiResponse.fail(401, e.getMessage())));
    }

    /**
     * Register a new customer account and auto-login (returns login's exact
     * {@code {token, refreshToken, userInfo}} shape — the SPA thunk expects it).
     * Duplicate username → 704; duplicate mobile → 705. Optional
     * {@code inviteCode} binds the account to a live promoter; an invite
     * problem never fails registration (Wave-5).
     */
    @PostMapping("/register")
    public Mono<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            LitemallUser user = account.register(
                    body.get("username"), body.get("password"), body.get("nickname"),
                    body.get("email"), body.get("mobile"), body.get("inviteCode"));
            return ApiResponse.ok(loginPayload(user));
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(AccountService.AccountException.class, this::fail);
    }

    @PostMapping("/refresh")
    public Mono<Map<String, Object>> refresh(@RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            RefreshTokenService.Rotation r = refreshTokens.rotate(body.get("refreshToken"));
            String access = jwt.issue(String.valueOf(r.getUserId()),
                    Map.of("uid", r.getUserId(), "typ", "customer"));
            Map<String, Object> data = new HashMap<>();
            data.put("token", access);
            data.put("refreshToken", r.getRefreshToken());
            return ApiResponse.ok(data);
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(InvalidRefreshTokenException.class,
                        e -> Mono.just(ApiResponse.fail(401, e.getMessage())));
    }

    @PostMapping("/logout")
    public Mono<Map<String, Object>> logout(@RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            refreshTokens.revoke(body.get("refreshToken"));
            return ApiResponse.ok(null);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Authenticated password change ({@code {oldPassword, newPassword}}).
     * Wrong old password → 700. On success every refresh token is revoked —
     * other sessions die at their next rotation.
     */
    @PostMapping("/reset")
    public Mono<Map<String, Object>> reset(
            @RequestHeader(value = IdentityForwardingFilter.HDR_USER_ID, required = false) String userId,
            @RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            account.changePassword(parseUserId(userId),
                    body.get("oldPassword"), body.get("newPassword"));
            return ApiResponse.ok(null);
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(AccountService.AccountException.class, this::fail);
    }

    /**
     * Forgot-password request ({@code {email}}). Disabled → 701; enabled →
     * errno 0 regardless of whether the email exists (anti-enumeration).
     */
    @PostMapping("/reset/request")
    public Mono<Map<String, Object>> resetRequest(@RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            account.requestReset(body.get("email"));
            return ApiResponse.ok(null);
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(AccountService.AccountException.class, this::fail);
    }

    /**
     * Forgot-password confirm ({@code {token, newPassword}}). Disabled → 701;
     * invalid/expired/used token → 703.
     */
    @PostMapping("/reset/confirm")
    public Mono<Map<String, Object>> resetConfirm(@RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            account.confirmReset(body.get("token"), body.get("newPassword"));
            return ApiResponse.ok(null);
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(AccountService.AccountException.class, this::fail);
    }

    /** Current account info. Replaces the dead {@code /srv/user/index} stub. */
    @GetMapping("/me")
    public Mono<Map<String, Object>> me(
            @RequestHeader(value = IdentityForwardingFilter.HDR_USER_ID, required = false) String userId) {
        return Mono.fromCallable(() ->
                ApiResponse.ok(userInfo(account.requireUser(parseUserId(userId)))))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(AccountService.AccountException.class, this::fail);
    }

    /**
     * Partial profile update ({@code {nickname?, email?, mobile?, avatar?,
     * gender?, birthday?}}); returns the refreshed account info.
     */
    @PostMapping("/profile")
    public Mono<Map<String, Object>> profile(
            @RequestHeader(value = IdentityForwardingFilter.HDR_USER_ID, required = false) String userId,
            @RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            // Accept both nickname and the SPA's legacy nickName key.
            String nickname = body.getOrDefault("nickname", body.get("nickName"));
            Byte gender = body.get("gender") == null ? null : Byte.valueOf(body.get("gender"));
            LitemallUser user = account.updateProfile(parseUserId(userId), nickname,
                    body.get("email"), body.get("mobile"), body.get("avatar"),
                    gender, body.get("birthday"));
            return ApiResponse.ok(userInfo(user));
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(AccountService.AccountException.class, this::fail)
                .onErrorResume(NumberFormatException.class,
                        e -> Mono.just(ApiResponse.fail(AccountService.ERR_BAD_ARGUMENT,
                                "gender must be numeric")));
    }

    /** Login/register response payload: access + refresh tokens + userInfo. */
    private Map<String, Object> loginPayload(LitemallUser user) {
        String access = jwt.issue(String.valueOf(user.getId()),
                Map.of("uid", user.getId(), "typ", "customer"));
        String refresh = refreshTokens.issue(user.getId(), LOGIN_TYPE);
        Map<String, Object> data = new HashMap<>();
        data.put("token", access);
        data.put("refreshToken", refresh);
        data.put("userInfo", userInfo(user));
        return data;
    }

    /** Account info map shared by login, register, /me and /profile. */
    private Map<String, Object> userInfo(LitemallUser user) {
        Map<String, Object> info = new HashMap<>();
        info.put("username", user.getUsername());
        // Real nickname on BOTH register and login; username only as fallback.
        info.put("nickName", user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername() : user.getNickname());
        info.put("avatarUrl", user.getAvatar());
        info.put("email", user.getEmail());
        info.put("mobile", user.getMobile());
        info.put("gender", user.getGender());
        info.put("birthday", user.getBirthday() == null ? null : user.getBirthday().toString());
        return info;
    }

    private Integer parseUserId(String header) {
        if (header == null || header.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(header);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Mono<Map<String, Object>> fail(AccountService.AccountException e) {
        return Mono.just(ApiResponse.fail(e.getErrno(), e.getMessage()));
    }
}
