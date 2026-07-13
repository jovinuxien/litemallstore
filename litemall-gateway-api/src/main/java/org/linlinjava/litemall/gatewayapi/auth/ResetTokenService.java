package org.linlinjava.litemall.gatewayapi.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.linlinjava.litemall.db.dao.LitemallUserResetTokenMapper;
import org.linlinjava.litemall.db.domain.LitemallUserResetToken;
import org.springframework.stereotype.Service;

/**
 * DB-backed single-use password-reset tokens (V37 table, V15 refresh-token
 * pattern): raw value returned once, only the SHA-256 hash persisted; 30-minute
 * expiry; consume flips {@code used} atomically so replay loses the race.
 *
 * <p>Blocking MyBatis — callers run on boundedElastic.
 */
@Service
public class ResetTokenService {

    /** Reset-token lifetime: 30 minutes. */
    private static final long TTL_MINUTES = 30L;

    private final LitemallUserResetTokenMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public ResetTokenService(LitemallUserResetTokenMapper mapper) {
        this.mapper = mapper;
    }

    /** Issue a new reset token for the user; returns the raw (un-hashed) value. */
    public String issue(Integer userId) {
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);

        LocalDateTime now = LocalDateTime.now();
        LitemallUserResetToken row = new LitemallUserResetToken();
        row.setUserId(userId);
        row.setTokenHash(sha256(raw));
        row.setExpiresAt(now.plusMinutes(TTL_MINUTES));
        row.setUsed(false);
        row.setAddTime(now);
        row.setUpdateTime(now);
        row.setDeleted(false);
        mapper.insertSelective(row);
        return raw;
    }

    /**
     * Validate and consume (single-use).
     *
     * @return the owning userId
     * @throws InvalidResetTokenException if unknown, already used, or expired
     */
    public Integer consume(String rawToken) {
        if (rawToken == null || rawToken.isEmpty()) {
            throw new InvalidResetTokenException();
        }
        String hash = sha256(rawToken);
        LitemallUserResetToken row = mapper.selectByTokenHash(hash);
        if (row == null || Boolean.TRUE.equals(row.getUsed())
                || row.getExpiresAt() == null
                || row.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidResetTokenException();
        }
        // Guarded update: a concurrent consume of the same token gets 0 rows.
        if (mapper.consumeByTokenHash(hash) == 0) {
            throw new InvalidResetTokenException();
        }
        return row.getUserId();
    }

    /** Invalidate every outstanding token for a user (after a successful reset). */
    public void invalidateAllForUser(Integer userId) {
        mapper.invalidateAllByUserId(userId);
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Presented reset token is unknown, used, or expired → errno 703. */
    public static class InvalidResetTokenException extends RuntimeException {
        public InvalidResetTokenException() {
            super("invalid or expired reset token");
        }
    }
}
