-- =============================================================================
-- V15 — Customer refresh tokens (DB-backed, rotating, revocable)
-- Backs the self-signed-JWT customer auth in litemall-wx-api.
-- Raw refresh tokens are never stored; only their SHA-256 hash.
-- Undo: db/undo/U15__undo_customer_refresh_token.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_user_refresh_token` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `user_id`     int(11) NOT NULL                COMMENT '用户ID',
  `token_hash`  char(64) NOT NULL              COMMENT '刷新令牌的SHA-256哈希(不存明文)',
  `login_type`  varchar(32) NOT NULL DEFAULT 'h5' COMMENT '登录类型 h5/wechat/mobile',
  `expires_at`  datetime NOT NULL              COMMENT '刷新令牌过期时间',
  `revoked`     tinyint(1) NOT NULL DEFAULT '0' COMMENT '0有效 1已吊销(登出或轮换)',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_token_hash` (`token_hash`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户刷新令牌表';
