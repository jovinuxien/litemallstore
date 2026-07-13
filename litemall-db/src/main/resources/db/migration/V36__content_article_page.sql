-- V36__content_article_page.sql
--
-- Wave 4 content subdomain (goods-management): article CMS + DIY pages.
-- Design: doc/wave4-plan-2026-07-13.pdf §3.3; contracts:
-- litemall-goods-management/docs/spec-page-palette-v1.md (NORMATIVE) +
-- handoff-content-endpoints.md.
--
--   litemall_article_category  -> flat category list (declared deviation from
--                                 crmeb, which reuses its shared category tree).
--   litemall_article           -> content is SANITIZED HTML (jsoup custom
--                                 Safelist, clean-and-store at write time);
--                                 view_count bumped by an atomic
--                                 `view_count = view_count + 1` statement.
--   litemall_page              -> palette-v1 JSON config (<=64KB app-enforced).
--                                 Exactly ONE active home page is enforced IN
--                                 SCHEMA: `active_home_slot` is a STORED
--                                 generated column that is 'home' only for an
--                                 active, non-deleted home page (NULL otherwise
--                                 — UNIQUE ignores NULLs), so a concurrent
--                                 double-activation loses with a duplicate-key
--                                 error instead of racing (`FOR UPDATE` on zero
--                                 rows serializes nothing).

CREATE TABLE IF NOT EXISTS `litemall_article_category` (
    `id`          INT NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(63) NOT NULL,
    `sort_order`  INT NOT NULL DEFAULT 100,
    `add_time`    DATETIME NULL,
    `update_time` DATETIME NULL,
    `deleted`     TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'article CMS categories (V36)';

CREATE TABLE IF NOT EXISTS `litemall_article` (
    `id`          INT NOT NULL AUTO_INCREMENT,
    `category_id` INT NOT NULL DEFAULT 0,
    `title`       VARCHAR(255) NOT NULL,
    `summary`     VARCHAR(511) NULL,
    `pic_url`     VARCHAR(255) NULL COMMENT 'cover image',
    `content`     MEDIUMTEXT NULL COMMENT 'sanitized HTML (jsoup custom Safelist, cleaned at write time)',
    `status`      VARCHAR(15) NOT NULL DEFAULT 'published' COMMENT 'published|hidden',
    `is_hot`      TINYINT(1) NOT NULL DEFAULT 0,
    `is_banner`   TINYINT(1) NOT NULL DEFAULT 0,
    `goods_id`    INT NOT NULL DEFAULT 0 COMMENT 'related goods, 0 = none',
    `view_count`  INT NOT NULL DEFAULT 0 COMMENT 'bumped atomically: view_count = view_count + 1',
    `add_time`    DATETIME NULL,
    `update_time` DATETIME NULL,
    `deleted`     TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_article_category` (`category_id`, `deleted`),
    KEY `idx_article_customer` (`status`, `deleted`, `add_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'article CMS (V36)';

CREATE TABLE IF NOT EXISTS `litemall_page` (
    `id`          INT NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(63) NOT NULL COMMENT 'admin-facing label',
    `position`    VARCHAR(15) NOT NULL DEFAULT 'custom' COMMENT 'home|custom',
    `config`      MEDIUMTEXT NOT NULL COMMENT 'palette v1 JSON (<=64KB, app-enforced; see spec-page-palette-v1.md)',
    `status`      VARCHAR(15) NOT NULL DEFAULT 'draft' COMMENT 'draft|active',
    `add_time`    DATETIME NULL,
    `update_time` DATETIME NULL,
    `deleted`     TINYINT(1) NOT NULL DEFAULT 0,
    `active_home_slot` VARCHAR(15) GENERATED ALWAYS AS (
        CASE WHEN `position` = 'home' AND `status` = 'active' AND `deleted` = 0
             THEN 'home' ELSE NULL END
    ) STORED COMMENT 'schema-enforced single active home: UNIQUE ignores NULLs',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_page_active_home` (`active_home_slot`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'DIY pages, palette v1 (V36)';

-- Seed a DRAFT default home so the admin editor has a starting point. Nothing
-- is ACTIVE: customer /srv/page/home keeps returning errno 642 and the SPA
-- keeps rendering the legacy hardcoded home until an admin activates a page.
INSERT INTO `litemall_page` (`name`, `position`, `config`, `status`, `add_time`, `update_time`)
VALUES ('Default Home', 'home', '{"version":1,"components":[]}', 'draft', NOW(), NOW());
