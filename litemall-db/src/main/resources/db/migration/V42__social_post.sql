-- =============================================================================
-- V42 — Social-post ledger (Wave 6, promotion social vertical)
--
-- One row per (goods, platform) publish attempt — manual composer posts and the
-- opt-in flash-deal auto-poster alike. The ledger is the audit surface for the
-- gateway-admin "Social posts" page (list / error tooltips / retry) and the
-- restart-safe dedupe store for the auto-poster:
--   deal_id      — the litemall_seckill row that triggered an auto post
--                  (NULL for manual composer posts)
--   auto_active  — 1 while the row is the armed auto post for its deal's
--                  CURRENT activation; the poller disarms (0) armed rows whose
--                  deal is no longer live, so a later re-activation posts a
--                  fresh row. Dedupe = at most one armed row per
--                  (deal, platform); see adr-social-publishing.md.
-- status transitions are guarded in SocialPostMapper: draft|failed -> posted,
-- draft|failed -> failed (a posted row is immutable). Retry re-fires failed
-- rows only.
-- =============================================================================

CREATE TABLE IF NOT EXISTS `litemall_social_post` (
  `id`               int(11) NOT NULL AUTO_INCREMENT,
  `goods_id`         int(11) NOT NULL COMMENT 'goods being promoted',
  `platform`         varchar(15) NOT NULL COMMENT 'meta_fb | meta_ig | tiktok',
  `caption`          text COMMENT 'post text as sent (or attempted)',
  `media_url`        varchar(511) DEFAULT NULL COMMENT 'image URL (meta_fb/meta_ig) or video URL (tiktok)',
  `link_url`         varchar(511) DEFAULT NULL COMMENT 'share URL carrying the UTM convention (utm_source/utm_medium=social/utm_campaign)',
  `status`           varchar(15) NOT NULL DEFAULT 'draft' COMMENT 'draft | posted | failed',
  `external_post_id` varchar(127) DEFAULT NULL COMMENT 'platform post/publish id on success',
  `error`            varchar(511) DEFAULT NULL COMMENT 'adapter-disabled or API error message on failure',
  `posted_by`        varchar(63) NOT NULL DEFAULT '' COMMENT 'admin id, or ''auto'' for the deal auto-poster',
  `deal_id`          int(11) DEFAULT NULL COMMENT 'litemall_seckill id for auto posts; NULL for manual',
  `auto_active`      tinyint(1) NOT NULL DEFAULT '0' COMMENT '1 = armed auto row for the deal''s current activation',
  `add_time`         datetime DEFAULT NULL,
  `update_time`      datetime DEFAULT NULL,
  `deleted`          tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_goods_id` (`goods_id`),
  KEY `idx_status_platform` (`status`, `platform`),
  KEY `idx_auto_dedupe` (`auto_active`, `deal_id`, `platform`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='social publish ledger (Meta FB/IG + TikTok), manual + deal auto-posts';
