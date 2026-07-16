package org.linlinjava.litemall.gatewayadmin.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.linlinjava.litemall.db.dao.LitemallUserRefreshTokenMapper;
import org.linlinjava.litemall.db.domain.LitemallUserRefreshToken;
import org.springframework.stereotype.Service;

/**
 * DB-backed, rotating, revocable AFFILIATE refresh tokens over the shared
 * customer table {@code litemall_user_refresh_token} (V15), discriminated by
 * {@code login_type='affiliate'}.
 *
 * <p>Slim edge service in the {@link AdminRefreshTokenService} mould — the
 * customer edge's RefreshTokenService lives in litemall-gateway-api and is not
 * reachable from this module, but the V15 mapper is (litemall-db). A customer
 * ({@code login_type='account'}-family) token presented here is rejected by
 * the login_type guard: the affiliate realm never mints an access token off a
 * customer session, and vice versa. Same hashing contract as V15/V16: raw
 * token returned once, only its SHA-256 stored, every refresh rotates.
 *
 * <p>Blocking MyBatis — callers run on a boundedElastic scheduler.
 */
@Service
public class AffiliateRefreshTokenService {

    public static final String LOGIN_TYPE = "affiliate";

    /** Refresh-token lifetime: 30 days (matches admin + customer realms). */
    private static final long TTL_DAYS = 30L;

    private final LitemallUserRefreshTokenMapper mapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public AffiliateRefreshTokenService(LitemallUserRefreshTokenMapper mapper,
                                        org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    /** Issue a new affiliate refresh token; returns the raw (un-hashed) value. */
    public String issue(Integer userId) {
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);

        LocalDateTime now = LocalDateTime.now();
        LitemallUserRefreshToken row = new LitemallUserRefreshToken();
        row.setUserId(userId);
        row.setTokenHash(sha256(raw));
        row.setLoginType(LOGIN_TYPE);
        row.setExpiresAt(now.plusDays(TTL_DAYS));
        row.setRevoked(false);
        row.setAddTime(now);
        row.setUpdateTime(now);
        row.setDeleted(false);
        mapper.insertSelective(row);
        return raw;
    }

    /**
     * Validate and rotate. Revokes the presented token and issues a fresh one.
     *
     * @return the rotation result (userId + new raw refresh token)
     * @throws InvalidRefreshTokenException if unknown, revoked, expired or not
     *                                      an affiliate-realm token
     */
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isEmpty()) {
            throw new InvalidRefreshTokenException("missing refresh token");
        }
        String hash = sha256(rawToken);
        LitemallUserRefreshToken row = mapper.selectByTokenHash(hash);
        if (row == null || Boolean.TRUE.equals(row.getRevoked())
                || !LOGIN_TYPE.equals(row.getLoginType())
                || row.getExpiresAt() == null
                || row.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException("invalid or expired refresh token");
        }
        mapper.revokeByTokenHash(hash);
        return new Rotation(row.getUserId(), issue(row.getUserId()));
    }

    /** Logout: revoke a single presented token (no-op if unknown). */
    public void revoke(String rawToken) {
        if (rawToken != null && !rawToken.isEmpty()) {
            mapper.revokeByTokenHash(sha256(rawToken));
        }
    }

    /**
     * Revoke every active AFFILIATE token for a user — e.g. when an admin
     * demotes a promoter. Scoped to {@code login_type='affiliate'} (direct
     * JDBC; the shared V15 mapper only has an unscoped revoke-all) so the
     * user's customer-storefront sessions survive the demotion.
     */
    public void revokeAllForUser(Integer userId) {
        jdbc.update("UPDATE litemall_user_refresh_token SET revoked = 1, update_time = NOW() "
                        + "WHERE user_id = ? AND login_type = ? AND revoked = 0 AND deleted = 0",
                userId, LOGIN_TYPE);
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Result of a successful rotation. */
    public static final class Rotation {
        private final Integer userId;
        private final String refreshToken;

        Rotation(Integer userId, String refreshToken) {
            this.userId = userId;
            this.refreshToken = refreshToken;
        }

        public Integer getUserId() {
            return userId;
        }

        public String getRefreshToken() {
            return refreshToken;
        }
    }
}
