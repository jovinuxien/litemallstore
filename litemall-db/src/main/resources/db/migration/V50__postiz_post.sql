-- =============================================================================
-- V50 — Postiz publish ledger (Wave 17, promotion social publishing)
--
-- One row per (goods, channel) Postiz publish attempt from the admin
-- "Social Publishing (Postiz)" panel. Feeds the panel's history tab
-- (GET /srv/private/admin/promotion/postiz/log) and the composer's
-- non-blocking "posted N days ago" dedup warnings.
--
--   integration_id     — Postiz integration (channel) id, a cuid string
--   channel_identifier — Postiz provider identifier (facebook | x | ...)
--   postiz_post_id     — Postiz's post id on success (posts appear in its
--                        calendar); NULL on failure
--   schedule_time      — the post's publish instant, stored as UTC (Postiz
--                        forces TZ=UTC; keep both sides in the same clock)
--   status             — scheduled | failed (Postiz owns the lifecycle after
--                        acceptance; we ledger the handoff, not the publish)
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_postiz_post` (
  `id`                 int(11) NOT NULL AUTO_INCREMENT,
  `goods_id`           int(11) NOT NULL COMMENT 'goods being promoted',
  `category_id`        int(11) DEFAULT NULL COMMENT 'category the admin picked from (informational)',
  `integration_id`     varchar(63) NOT NULL COMMENT 'Postiz integration (channel) id',
  `channel_identifier` varchar(31) NOT NULL COMMENT 'Postiz provider identifier (facebook | x | instagram | ...)',
  `postiz_post_id`     varchar(127) DEFAULT NULL COMMENT 'Postiz post id on success',
  `schedule_time`      datetime DEFAULT NULL COMMENT 'publish instant, UTC',
  `status`             varchar(15) NOT NULL DEFAULT 'scheduled' COMMENT 'scheduled | failed',
  `error`              varchar(511) DEFAULT NULL COMMENT 'Postiz validation/API error, surfaced verbatim',
  `posted_by`          varchar(63) NOT NULL DEFAULT '' COMMENT 'admin id',
  `add_time`           datetime DEFAULT NULL,
  `update_time`        datetime DEFAULT NULL,
  `deleted`            tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_goods_id` (`goods_id`),
  KEY `idx_add_time` (`add_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Postiz publish ledger (Wave 17 social publishing)';
