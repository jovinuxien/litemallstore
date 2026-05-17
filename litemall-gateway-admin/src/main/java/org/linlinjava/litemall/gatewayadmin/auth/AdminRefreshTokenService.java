package org.linlinjava.litemall.gatewayadmin.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.linlinjava.litemall.db.dao.LitemallAdminRefreshTokenMapper;
import org.linlinjava.litemall.db.domain.LitemallAdminRefreshToken;
import org.springframework.stereotype.Service;

/**
 * DB-backed, rotating, revocable admin refresh tokens (V16 table).
 *
 * <p>Mirror of the customer {@code RefreshTokenService} (V15) but a separate
 * admin table — admin and customer refresh tokens are never interchangeable.
 * The raw token is returned to the client once and never stored — only its
 * SHA-256 hash is persisted. Every refresh rotates: the presented token is
 * revoked and a fresh one issued, so a stolen-and-replayed token is single-use.
 *
 * <p>Blocking MyBatis. Callers (the reactive auth controller) run these on a
 * boundedElastic scheduler.
 */
@Service
public class AdminRefreshTokenService {

    /** Refresh-token lifetime: 30 days. */
    private static final long TTL_DAYS = 30L;

    private final LitemallAdminRefreshTokenMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public AdminRefreshTokenService(LitemallAdminRefreshTokenMapper mapper) {
        this.mapper = mapper;
    }

    /** Issue a new refresh token for the admin; returns the raw (un-hashed) value. */
    public String issue(Integer adminId, String loginType) {
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);

        LocalDateTime now = LocalDateTime.now();
        LitemallAdminRefreshToken row = new LitemallAdminRefreshToken();
        row.setAdminId(adminId);
        row.setTokenHash(sha256(raw));
        row.setLoginType(loginType == null ? "admin" : loginType);
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
     * @return the rotation result (adminId + new raw refresh token)
     * @throws InvalidRefreshTokenException if unknown, revoked or expired
     */
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isEmpty()) {
            throw new InvalidRefreshTokenException("missing refresh token");
        }
        String hash = sha256(rawToken);
        LitemallAdminRefreshToken row = mapper.selectByTokenHash(hash);
        if (row == null || Boolean.TRUE.equals(row.getRevoked())
                || row.getExpiresAt() == null
                || row.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException("invalid or expired refresh token");
        }
        mapper.revokeByTokenHash(hash);
        String next = issue(row.getAdminId(), row.getLoginType());
        return new Rotation(row.getAdminId(), next);
    }

    /** Logout: revoke a single presented token (no-op if unknown). */
    public void revoke(String rawToken) {
        if (rawToken != null && !rawToken.isEmpty()) {
            mapper.revokeByTokenHash(sha256(rawToken));
        }
    }

    /** Revoke every active token for an admin (e.g. global logout / password change). */
    public void revokeAllForAdmin(Integer adminId) {
        mapper.revokeAllByAdminId(adminId);
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
        private final Integer adminId;
        private final String refreshToken;

        Rotation(Integer adminId, String refreshToken) {
            this.adminId = adminId;
            this.refreshToken = refreshToken;
        }

        public Integer getAdminId() {
            return adminId;
        }

        public String getRefreshToken() {
            return refreshToken;
        }
    }
}
