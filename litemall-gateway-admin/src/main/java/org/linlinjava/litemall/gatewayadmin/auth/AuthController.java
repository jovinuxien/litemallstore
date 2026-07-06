package org.linlinjava.litemall.gatewayadmin.auth;

import java.util.HashMap;
import java.util.Map;

import org.linlinjava.litemall.db.auth.JwtService;
import org.linlinjava.litemall.db.domain.LitemallAdmin;
import org.linlinjava.litemall.gatewayadmin.infrastructure.config.security.AuthoritiesConstants;
import org.linlinjava.litemall.gatewayadmin.web.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Admin authentication at the edge (BFF).
 *
 * <p>Issues/validates a self-signed admin JWT here — never relayed downstream,
 * no Keycloak. Refresh tokens are DB-backed and rotating (V16 table). Every
 * non-deleted litemall_admin is {@code ROLE_ADMIN} (minimal claim model);
 * granular RBAC is a later concern. Credential and DB work is blocking
 * MyBatis, run on a boundedElastic scheduler so the Netty event loop is never
 * blocked. Responses use the litemall {@code {errno,errmsg,data}} envelope.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String LOGIN_TYPE = "admin";

    private final AdminCredentialsService credentials;
    private final AdminRefreshTokenService refreshTokens;
    private final JwtService jwt;

    public AuthController(AdminCredentialsService credentials,
                          AdminRefreshTokenService refreshTokens,
                          JwtService adminJwtService) {
        this.credentials = credentials;
        this.refreshTokens = refreshTokens;
        this.jwt = adminJwtService;
    }

    @PostMapping("/login")
    public Mono<ApiResponse<Object>> login(@RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            LitemallAdmin admin = credentials.authenticate(
                    body.get("username"), body.get("password"));
            String access = jwt.issue(String.valueOf(admin.getId()),
                    Map.of("uid", admin.getId(), "typ", "admin",
                            // roles MUST be a String[] (not List): JwtService.addClaim
                            // only serializes String/Number/Boolean/String[] as native
                            // claims — a List falls through to String.valueOf() and is
                            // stored as "[ROLE_ADMIN]", which IdentityForwardingFilter
                            // cannot read back via getClaim("roles").asList(...). Then no
                            // X-User-Roles is forwarded and downstream returns 403.
                            "roles", new String[] { AuthoritiesConstants.ADMIN }));
            String refresh = refreshTokens.issue(admin.getId(), LOGIN_TYPE);

            Map<String, Object> adminInfo = new HashMap<>();
            adminInfo.put("nickName", admin.getUsername());
            adminInfo.put("avatarUrl", admin.getAvatar());

            Map<String, Object> data = new HashMap<>();
            data.put("token", access);
            data.put("refreshToken", refresh);
            data.put("adminInfo", adminInfo);
            return ApiResponse.<Object>ok(data);
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(AdminCredentialsService.BadCredentialsException.class,
                        e -> Mono.just(ApiResponse.fail(401, e.getMessage())));
    }

    @PostMapping("/refresh")
    public Mono<ApiResponse<Object>> refresh(@RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            AdminRefreshTokenService.Rotation r = refreshTokens.rotate(body.get("refreshToken"));
            String access = jwt.issue(String.valueOf(r.getAdminId()),
                    Map.of("uid", r.getAdminId(), "typ", "admin",
                            // roles MUST be a String[] (not List): JwtService.addClaim
                            // only serializes String/Number/Boolean/String[] as native
                            // claims — a List falls through to String.valueOf() and is
                            // stored as "[ROLE_ADMIN]", which IdentityForwardingFilter
                            // cannot read back via getClaim("roles").asList(...). Then no
                            // X-User-Roles is forwarded and downstream returns 403.
                            "roles", new String[] { AuthoritiesConstants.ADMIN }));
            Map<String, Object> data = new HashMap<>();
            data.put("token", access);
            data.put("refreshToken", r.getRefreshToken());
            return ApiResponse.<Object>ok(data);
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(InvalidRefreshTokenException.class,
                        e -> Mono.just(ApiResponse.fail(401, e.getMessage())));
    }

    @PostMapping("/logout")
    public Mono<ApiResponse<Object>> logout(@RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            refreshTokens.revoke(body.get("refreshToken"));
            return ApiResponse.<Object>ok(null);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
