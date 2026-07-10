-- =============================================================================
-- V30 — Combination group-buy PARTICIPATION ("pink", crmeb StorePink pattern)
-- Owned by litemall-promotion-service. A row is one participant slot; the
-- leader's row has head_id = 0 and the group's identity is the leader's row id
-- (members carry it in head_id). required_members / expire_time are snapshots
-- of the campaign rules at group start, so a later rule edit cannot mutate a
-- running group. litemall-order's legacy litemall_groupon tables are untouched
-- (see litemall-promotion-service/docs/adr-combination-groupon-split.md).
-- Undo: db/undo/U30__undo_combination_pink.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_combination_pink` (
  `id`               int(11) NOT NULL AUTO_INCREMENT,
  `combination_id`   int(11) NOT NULL COMMENT '拼团活动ID',
  `head_id`          int(11) NOT NULL DEFAULT '0' COMMENT '团长pink id, 0=本行是团长',
  `user_id`          int(11) NOT NULL COMMENT '参团用户ID',
  `order_id`         int(11)          DEFAULT NULL COMMENT '关联订单ID(支付后回填)',
  `required_members` int(11) NOT NULL DEFAULT '2' COMMENT '成团所需人数(快照)',
  `expire_time`      datetime NOT NULL COMMENT '成团截止时间',
  `status`           tinyint(1) NOT NULL DEFAULT '0' COMMENT '状态 0拼团中 1成功 2失败',
  `add_time`         datetime DEFAULT NULL,
  `update_time`      datetime DEFAULT NULL,
  `deleted`          tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_combination_id` (`combination_id`),
  KEY `idx_head_id` (`head_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status_expire` (`status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团参与(团/成员)表';
