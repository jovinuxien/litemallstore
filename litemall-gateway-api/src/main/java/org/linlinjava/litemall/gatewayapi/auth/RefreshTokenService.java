package org.linlinjava.litemall.gatewayapi.auth;

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
 * DB-backed, rotating, revocable customer refresh tokens (V15 table).
 *
 * <p>The raw token is returned to the client once and never stored — only its
 * SHA-256 hash is persisted (see {@code V15__add_customer_refresh_token.sql}).
 * Every refresh rotates: the presented token is revoked and a fresh one issued,
 * so a stolen-and-replayed token is single-use.
 *
 * <p>Blocking MyBatis. Callers (the reactive auth controller) run these on a
 * boundedElastic scheduler.
 */
@Service
public class RefreshTokenService {

    /** Refresh-token lifetime: 30 days. */
    private static final long TTL_DAYS = 30L;

    private final LitemallUserRefreshTokenMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(LitemallUserRefreshTokenMapper mapper) {
        this.mapper = mapper;
    }

    /** Issue a new refresh token for the user; returns the raw (un-hashed) value. */
    public String issue(Integer userId, String loginType) {
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);

        LocalDateTime now = LocalDateTime.now();
        LitemallUserRefreshToken row = new LitemallUserRefreshToken();
        row.setUserId(userId);
        row.setTokenHash(sha256(raw));
        row.setLoginType(loginType == null ? "h5" : loginType);
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
     * @throws InvalidRefreshTokenException if unknown, revoked or expired
     */
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isEmpty()) {
            throw new InvalidRefreshTokenException("missing refresh token");
        }
        String hash = sha256(rawToken);
        LitemallUserRefreshToken row = mapper.selectByTokenHash(hash);
        if (row == null || Boolean.TRUE.equals(row.getRevoked())
                || row.getExpiresAt() == null
                || row.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException("invalid or expired refresh token");
        }
        mapper.revokeByTokenHash(hash);
        String next = issue(row.getUserId(), row.getLoginType());
        return new Rotation(row.getUserId(), next);
    }

    /** Logout: revoke a single presented token (no-op if unknown). */
    public void revoke(String rawToken) {
        if (rawToken != null && !rawToken.isEmpty()) {
            mapper.revokeByTokenHash(sha256(rawToken));
        }
    }

    /** Revoke every active token for a user (e.g. global logout / password change). */
    public void revokeAllForUser(Integer userId) {
        mapper.revokeAllByUserId(userId);
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