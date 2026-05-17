package org.linlinjava.litemall.gatewayapi.auth;

import java.util.HashMap;
import java.util.Map;

import org.linlinjava.litemall.db.auth.JwtService;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.gatewayapi.web.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Customer authentication at the edge (BFF).
 *
 * <p>Issues/validates a self-signed customer JWT here — never relayed
 * downstream, no Keycloak. Refresh tokens are DB-backed and rotating
 * (V15 table). Credential and DB work is blocking MyBatis, run on a
 * boundedElastic scheduler so the Netty event loop is never blocked.
 * Responses use the litemall {@code {errno,errmsg,data}} envelope.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String LOGIN_TYPE = "h5";

    private final CustomerCredentialsService credentials;
    private final RefreshTokenService refreshTokens;
    private final JwtService jwt;

    public AuthController(CustomerCredentialsService credentials,
                          RefreshTokenService refreshTokens,
                          JwtService customerJwtService) {
        this.credentials = credentials;
        this.refreshTokens = refreshTokens;
        this.jwt = customerJwtService;
    }

    @PostMapping("/login")
    public Mono<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            LitemallUser user = credentials.authenticate(
                    body.get("username"), body.get("password"));
            String access = jwt.issue(String.valueOf(user.getId()),
                    Map.of("uid", user.getId(), "typ", "customer"));
            String refresh = refreshTokens.issue(user.getId(), LOGIN_TYPE);

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("nickName", user.getUsername());
            userInfo.put("avatarUrl", user.getAvatar());

            Map<String, Object> data = new HashMap<>();
            data.put("token", access);
            data.put("refreshToken", refresh);
            data.put("userInfo", userInfo);
            return ApiResponse.ok(data);
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(CustomerCredentialsService.BadCredentialsException.class,
                        e -> Mono.just(ApiResponse.fail(401, e.getMessage())));
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
}