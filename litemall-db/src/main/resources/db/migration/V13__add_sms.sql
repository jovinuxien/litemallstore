-- =============================================================================
-- V13 — SMS notification tables (record + template)
-- Undo: db/undo/U13__undo_sms.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_sms_template` (
  `id`            int(11) NOT NULL AUTO_INCREMENT,
  `template_code` varchar(64) NOT NULL DEFAULT '' COMMENT '短信平台模板CODE',
  `type`          tinyint(1) NOT NULL DEFAULT '1'  COMMENT '类型 1验证码 2通知 3营销',
  `title`         varchar(128) NOT NULL DEFAULT '' COMMENT '模板标题',
  `content`       varchar(512) NOT NULL DEFAULT '' COMMENT '模板内容(含占位符)',
  `status`        tinyint(1) NOT NULL DEFAULT '1'  COMMENT '1启用 0禁用',
  `add_time`      datetime DEFAULT NULL,
  `update_time`   datetime DEFAULT NULL,
  `deleted`       tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_template_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信模板表';

CREATE TABLE IF NOT EXISTS `litemall_sms_record` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `user_id`     int(11)             DEFAULT '0'  COMMENT '用户ID 0=非注册用户',
  `mobile`      varchar(20) NOT NULL DEFAULT ''   COMMENT '目标手机号',
  `template_id` int(11)             DEFAULT NULL  COMMENT '模板ID',
  `content`     varchar(512)        DEFAULT ''    COMMENT '实际发送内容',
  `type`        varchar(32) NOT NULL DEFAULT ''   COMMENT '业务类型 verify_code/order_ship等',
  `status`      tinyint(1) NOT NULL DEFAULT '0'   COMMENT '0发送中 1成功 -1失败',
  `error_msg`   varchar(255)        DEFAULT ''    COMMENT '失败原因',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_mobile` (`mobile`),
  KEY `idx_type` (`type`),
  KEY `idx_add_time` (`add_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信发送记录表';
