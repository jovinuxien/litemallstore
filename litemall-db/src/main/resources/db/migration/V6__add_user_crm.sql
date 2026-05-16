-- =============================================================================
-- V6 — User CRM tables (groups, tags, auth tokens)
-- Undo: db/undo/U6__undo_user_crm.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_user_group` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `group_name`  varchar(64) NOT NULL DEFAULT '' COMMENT '分组名称',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户分组表';

CREATE TABLE IF NOT EXISTS `litemall_user_tag` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `tag_name`    varchar(64) NOT NULL DEFAULT '' COMMENT '标签名称',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户标签表';

CREATE TABLE IF NOT EXISTS `litemall_user_token` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `user_id`     int(11) NOT NULL COMMENT '用户ID',
  `token`       varchar(255) NOT NULL COMMENT 'access token',
  `token_type`  varchar(32)  NOT NULL DEFAULT '' COMMENT '登录类型 h5/wechat/routine',
  `expires_at`  datetime DEFAULT NULL COMMENT 'token过期时间',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_token` (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户Token表';