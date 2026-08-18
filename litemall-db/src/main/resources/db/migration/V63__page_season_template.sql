-- V63__page_season_template.sql
--
-- Wave 27 (2026-08-18): seasonal merchandising collection.
--
-- Background: the storefront's "Summer Deals" strip was never a collection. It was a
-- hardcoded keyword search (`q=summer&source=cj`) with its label hardcoded in three SPA
-- files and no admin surface at all — nothing to select, order, schedule or switch off.
-- After the Wave-26 narrowing it returned 39 loosely-matching products, one of which
-- ("Diving Tube Super Bright Flashlight") does not contain the word "summer" anywhere.
-- A season is now an ordinary DIY page carrying category 'season', which means it
-- inherits the editor, the draft/active lifecycle, the clone flow and Postiz publishing
-- that already exist.
--
-- No structural change is needed for the category itself: `category` is VARCHAR(31) with
-- no DB-level constraint, so 'season' only had to join the application whitelist
-- (AdminPageController.CATEGORIES). This migration therefore does two small things:
-- refresh the now-stale column comment, and seed ONE designed template.
--
-- The seeded rail is deliberately `mode=deals`, NOT `mode=byIds`: the palette validator
-- requires 1-24 REAL ids for byIds, and seeding placeholder ids would either render an
-- empty rail or — worse, if the ids happen to exist — silently advertise products nobody
-- chose. Curating the rail into `byIds` is step 3 of the documented procedure
-- (litemall-goods-management/docs/spec-season-collection.md); a clone that has only had
-- its copy edited still renders something sensible.
--
-- Seeded as a DRAFT TEMPLATE, so nothing is customer-visible until an admin clones it and
-- activates the clone. The config is parsed OUT OF THIS FILE by PageTemplateSeedTest and
-- run through the live palette validator: keep the config value on ONE line, free of
-- single quotes.

ALTER TABLE `litemall_page`
    MODIFY COLUMN `category` VARCHAR(31) NOT NULL DEFAULT 'general'
        COMMENT 'general|coupon|groupon|season (season = the storefront season strip, Wave 27)';

-- Template 3: "Season spotlight" — seasonal merchandising layout.
-- Headline -> curated season rail -> what-changed explainer.
INSERT INTO `litemall_page` (`name`, `position`, `category`, `config`, `status`, `is_template`, `add_time`, `update_time`)
VALUES ('Season spotlight', 'custom', 'season',
'{"version":1,"components":[{"type":"rich-text","key":"hero","config":{"html":"<div><h1>This Season at Trovemo</h1><p>A short, hand-picked selection for the time of year &mdash; chosen from our home, garden and tool ranges rather than pulled by a keyword.</p></div>"}},{"type":"goods-list","key":"season-rail","config":{"mode":"deals","limit":8,"title":"This season&rsquo;s picks"}},{"type":"rich-text","key":"why","config":{"html":"<div><h2>Why these</h2><p>Every product here was selected by hand and ordered deliberately. When the season turns, the selection is replaced &mdash; the previous one is kept, not deleted, so it can come back next year.</p></div>"}}]}',
'draft', 1, NOW(), NOW());
