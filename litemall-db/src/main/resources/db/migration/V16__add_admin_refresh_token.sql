-- =============================================================================
-- V16 — Admin refresh tokens (DB-backed, rotating, revocable)
-- Backs the self-signed-JWT admin auth in litemall-gateway-admin.
-- Mirrors V15 (customer) but a separate table for the admin realm — admin and
-- customer refresh tokens are never interchangeable.
-- Raw refresh tokens are never stored; only their SHA-256 hash.
-- Undo: db/undo/U16__undo_admin_refresh_token.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_admin_refresh_token` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `admin_id`    int(11) NOT NULL                COMMENT '管理员ID',
  `token_hash`  char(64) NOT NULL              COMMENT '刷新令牌的SHA-256哈希(不存明文)',
  `login_type`  varchar(32) NOT NULL DEFAULT 'admin' COMMENT '登录类型 admin',
  `expires_at`  datetime NOT NULL              COMMENT '刷新令牌过期时间',
  `revoked`     tinyint(1) NOT NULL DEFAULT '0' COMMENT '0有效 1已吊销(登出或轮换)',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_admin_token_hash` (`token_hash`),
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_admin_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员刷新令牌表';