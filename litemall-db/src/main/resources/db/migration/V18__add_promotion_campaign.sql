-- =============================================================================
-- V18 — Promotion CAMPAIGN (algorithmic targeting) table
-- Owned by litemall-promotion-service (Phase 2). A campaign carries targeting
-- criteria (RFM/segment thresholds), the target goods + a linked Phase-1 promo
-- mechanic, a schedule, and a budget/cap. The targeting engine evaluates the
-- criteria against real customer statistics and produces a promotion
-- assignment (audience), emitting PromotionTargetedEvent.
-- See litemall-promotion-service/docs/phase2-targeting-pipeline.md.
-- Undo: db/undo/U18__undo_promotion_campaign.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_promotion_campaign` (
  `id`                    int(11) NOT NULL AUTO_INCREMENT,
  `name`                  varchar(255) NOT NULL DEFAULT '' COMMENT '营销活动名称',
  -- Targeting criteria (RFM/segment). Segments stored as a CSV of segment
  -- names; the score floors are optional additional gates.
  `target_segments`       varchar(255)          DEFAULT ''  COMMENT '目标客户分群(CSV)',
  `min_recency_score`     tinyint(2)            DEFAULT NULL COMMENT 'R分下限 1-5',
  `min_frequency_score`   tinyint(2)            DEFAULT NULL COMMENT 'F分下限 1-5',
  `min_monetary_score`    tinyint(2)            DEFAULT NULL COMMENT 'M分下限 1-5',
  -- Target goods + linked Phase-1 promo mechanic (reused, not duplicated).
  `target_goods_ids`      varchar(512)          DEFAULT ''  COMMENT '目标商品ID(CSV)',
  `linked_promotion_type` varchar(32)           DEFAULT NULL COMMENT '关联促销类型 COUPON/SECKILL/BARGAIN/COMBINATION',
  `linked_promotion_id`   int(11)               DEFAULT NULL COMMENT '关联促销活动ID',
  -- Schedule.
  `start_time`            datetime              DEFAULT NULL COMMENT '活动开始时间',
  `end_time`              datetime              DEFAULT NULL COMMENT '活动结束时间',
  -- Budget / cap (Katsov §3.6.2). Either may be null (uncapped).
  `max_audience`          int(11)               DEFAULT NULL COMMENT '受众人数上限 null不限',
  `max_spend`             decimal(10,2)         DEFAULT NULL COMMENT '预算上限 null不限',
  `assigned_count`        int(11) NOT NULL DEFAULT '0' COMMENT '最近一次评估命中的受众数',
  `spent_budget`          decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '已消耗预算',
  `status`                tinyint(1) NOT NULL DEFAULT '0' COMMENT '状态 0草稿 1进行中 2已完成 3暂停',
  `add_time`              datetime DEFAULT NULL,
  `update_time`           datetime DEFAULT NULL,
  `deleted`               tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_linked_promotion` (`linked_promotion_type`, `linked_promotion_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营销活动(算法化定向)表';
