-- =============================================================================
-- V4 — User engagement tables (points, check-in, experience)
-- Undo: db/undo/U4__undo_user_engagement.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_user_integral_record` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `user_id`     int(11) NOT NULL COMMENT '用户ID',
  `link_id`     varchar(32) NOT NULL DEFAULT '0' COMMENT '关联业务ID',
  `link_type`   varchar(32)          DEFAULT ''  COMMENT '关联类型 order/sign/activity等',
  `title`       varchar(64) NOT NULL DEFAULT ''  COMMENT '标题',
  `number`      int(11) NOT NULL DEFAULT '0'     COMMENT '积分变动量(正增负减)',
  `balance`     int(11) NOT NULL DEFAULT '0'     COMMENT '变动后积分余额',
  `mark`        varchar(255)         DEFAULT ''  COMMENT '备注',
  `status`      tinyint(1) NOT NULL DEFAULT '1'  COMMENT '1有效 0无效',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_link_type` (`link_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户积分记录表';

CREATE TABLE IF NOT EXISTS `litemall_user_sign` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `user_id`     int(11) NOT NULL COMMENT '用户ID',
  `integral`    int(11) NOT NULL DEFAULT '0' COMMENT '签到获得积分',
  `add_time`    datetime DEFAULT NULL COMMENT '签到时间',
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_add_time` (`add_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户签到记录表';

CREATE TABLE IF NOT EXISTS `litemall_user_experience_record` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `user_id`     int(11) NOT NULL COMMENT '用户ID',
  `link_id`     varchar(32) NOT NULL DEFAULT '0' COMMENT '关联业务ID',
  `link_type`   varchar(32)          DEFAULT ''  COMMENT '关联类型 order/sign等',
  `title`       varchar(64) NOT NULL DEFAULT ''  COMMENT '标题',
  `experience`  int(11) NOT NULL DEFAULT '0'     COMMENT '经验变动值(正增负减)',
  `balance`     int(11) NOT NULL DEFAULT '0'     COMMENT '变动后经验总量',
  `mark`        varchar(255)         DEFAULT ''  COMMENT '备注',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted`     tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户经验值记录表';