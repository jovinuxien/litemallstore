package org.linlinjava.litemall.gatewayadmin.auth;

import java.util.HashMap;
import java.util.Map;

import org.linlinjava.litemall.db.auth.JwtService;
import org.linlinjava.litemall.gatewayadmin.auth.AffiliateUserStore.AffiliateUser;
import org.linlinjava.litemall.gatewayadmin.infrastructure.config.security.AuthoritiesConstants;
import org.linlinjava.litemall.gatewayadmin.web.ApiResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Affiliate (promoter) authentication at the admin edge — Wave 5.
 *
 * <p>Mirrors {@link AuthController} but authenticates against
 * {@code litemall_user} (NEVER litemall_admin): same BCrypt family as
 * AdminCredentialsService/gateway-api, plus the affiliate gate —
 * {@code is_promoter=1} or 403 "not an affiliate". Issues the SAME admin-edge
 * realm JWT with {@code typ=affiliate} and roles {@code [ROLE_AFFILIATE]}
 * (String[], see the serialization note in AuthController), so
 * {@code AdminJwtAuthenticationManager} / {@code IdentityForwardingFilter}
 * work unchanged while SecurityConfig keeps the two surfaces disjoint.
 * Refresh tokens ride the shared V15 customer table with
 * {@code login_type='affiliate'} ({@link AffiliateRefreshTokenService});
 * every refresh re-checks the user is still a live promoter, so a demoted or
 * deleted affiliate is cut off at the next rotation.
 */
@RestController
@RequestMapping("/auth/affiliate")
public class AffiliateAuthController {

    private static final int UNAUTHORIZED = 401;
    private static final int NOT_AFFILIATE = 403;

    private final AffiliateUserStore users;
    private final AffiliateRefreshTokenService refreshTokens;
    private final JwtService jwt;
    /** Same encoder family as AdminCredentialsService / gateway-api's customer auth. */
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AffiliateAuthController(AffiliateUserStore users,
                                   AffiliateRefreshTokenService refreshTokens,
                                   JwtService adminJwtService) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.jwt = adminJwtService;
    }

    @PostMapping("/login")
    public Mono<ApiResponse<Object>> login(@RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            String username = body.get("username");
            String password = body.get("password");
            if (username == null || password == null) {
                return ApiResponse.fail(UNAUTHORIZED, "username and password are required");
            }
            AffiliateUser user = users.findLiveByUsername(username);
            if (user == null || !encoder.matches(password, user.getPassword())) {
                return ApiResponse.fail(UNAUTHORIZED, "invalid username or password");
            }
            if (!user.isPromoter()) {
                return ApiResponse.fail(NOT_AFFILIATE, "not an affiliate");
            }
            return ApiResponse.<Object>ok(tokenPayload(user, refreshTokens.issue(user.getId())));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/refresh")
    public Mono<ApiResponse<Object>> refresh(@RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            AffiliateRefreshTokenService.Rotation r =
                    refreshTokens.rotate(body.get("refreshToken"));
            // Re-gate on every rotation: deleted or demoted users lose the realm.
            AffiliateUser user = users.findLiveById(r.getUserId());
            if (user == null || !user.isPromoter()) {
                refreshTokens.revoke(r.getRefreshToken());
                return ApiResponse.fail(NOT_AFFILIATE, "not an affiliate");
            }
            return ApiResponse.<Object>ok(tokenPayload(user, r.getRefreshToken()));
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(InvalidRefreshTokenException.class,
                        e -> Mono.just(ApiResponse.fail(UNAUTHORIZED, e.getMessage())));
    }

    @PostMapping("/logout")
    public Mono<ApiResponse<Object>> logout(@RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            refreshTokens.revoke(body.get("refreshToken"));
            return ApiResponse.<Object>ok(null);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Map<String, Object> tokenPayload(AffiliateUser user, String refreshToken) {
        String access = jwt.issue(String.valueOf(user.getId()),
                Map.of("uid", user.getId(), "typ", "affiliate",
                        // String[] — see the JwtService.addClaim note in AuthController.
                        "roles", new String[] { AuthoritiesConstants.AFFILIATE }));
        Map<String, Object> info = new HashMap<>();
        info.put("nickName", user.getNickname() == null || user.getNickname().isEmpty()
                ? user.getUsername() : user.getNickname());
        info.put("avatarUrl", user.getAvatar());

        Map<String, Object> data = new HashMap<>();
        data.put("token", access);
        data.put("refreshToken", refreshToken);
        data.put("affiliateInfo", info);
        return data;
    }
}
