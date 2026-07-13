package org.linlinjava.litemall.db.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * V37: password-reset token (email flow). Raw token is never stored — only its
 * SHA-256 hash. 30-minute expiry, single-use ({@code used} flips on consume).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LitemallUserResetToken {

    private Integer       id;
    /** User ID */
    private Integer       userId;
    /** SHA-256 hash of the reset token (raw value never stored) */
    private String        tokenHash;
    /** Token expiry (30 minutes after issue) */
    private LocalDateTime expiresAt;
    /** 0 unused, 1 consumed or invalidated (single-use) */
    private Boolean       used;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean       deleted;
}
