-- =============================================================================
-- V37 — Account self-service: user email + password-reset tokens
-- Backs gateway-api /auth/register|reset|profile|me (Wave 4 Task A).
-- Raw reset tokens are never stored; only their SHA-256 hash (V15 pattern).
-- 30-minute expiry, single-use. NOTE: username unique index already exists
-- (user_name) — deliberately NOT re-created here.
--
-- IDEMPOTENT BY DESIGN: the shared dev DB received this DDL manually during
-- Wave 4 (gateway-api runs Flyway-disabled; registering V37 in history before
-- the siblings' V34–V36 would have blocked them under out-of-order: false).
-- The conditional ALTER lets Flyway apply this file cleanly post-merge even
-- where the column already exists.
-- =============================================================================

SET @email_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'litemall_user'
    AND COLUMN_NAME = 'email'
);
SET @ddl := IF(@email_exists = 0,
  'ALTER TABLE `litemall_user` ADD COLUMN `email` varchar(127) DEFAULT NULL COMMENT ''Email address (optional, for password reset)'' AFTER `mobile`',
  'SELECT 1');
PREPARE email_stmt FROM @ddl;
EXECUTE email_stmt;
DEALLOCATE PREPARE email_stmt;

CREATE TABLE IF NOT EXISTS `litemall_user_reset_token` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `user_id`     int(11) NOT NULL                COMMENT 'User ID',
  `token_hash`  char(64) NOT NULL               COMMENT 'SHA-256 hash of the reset token (raw value never stored)',
  `expires_at`  datetime NOT NULL               COMMENT 'Token expiry (30 minutes after issue)',
  `used`        tinyint(1) NOT NULL DEFAULT '0' COMMENT '0 unused, 1 consumed or invalidated (single-use)',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_reset_token_hash` (`token_hash`),
  KEY `idx_reset_user_id` (`user_id`),
  KEY `idx_reset_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Password reset tokens';
