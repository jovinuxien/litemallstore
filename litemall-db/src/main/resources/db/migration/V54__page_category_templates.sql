-- V54__page_category_templates.sql
--
-- Wave 20 (2026-08-08): DIY promo pages — page category + designed templates.
-- Contracts: litemall-goods-management/docs/spec-page-palette-v1.md (NORMATIVE,
-- v1.1 section) + the Wave-20 CONTRACT block in CLAUDE.md.
--
--   category    -> merchandising intent of the page. 'groupon' pages are the
--                  ones promotion's Postiz publisher refuses until Phase 3
--                  (the gating decision); customer render is unchanged (any
--                  ACTIVE page serves).
--   is_template -> seed-only designed starting points ("New from template" in
--                  admin clones them). READ-ONLY through the API: clones are
--                  normal pages (is_template = 0), and no endpoint ever writes
--                  the flag.
--
-- Two templates are seeded below (draft + custom — never active, so nothing
-- changes customer-side until an admin clones/activates). Their config JSON is
-- palette v1.1 and is drift-checked by goods-management's PageTemplateSeedTest,
-- which parses THIS file: keep each config value on its own single line and
-- free of single quotes.

ALTER TABLE `litemall_page`
    ADD COLUMN `category` VARCHAR(31) NOT NULL DEFAULT 'general'
        COMMENT 'general|coupon|groupon (Postiz refuses groupon pages until Phase 3)'
        AFTER `position`,
    ADD COLUMN `is_template` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT 'seed-only designed template; read-only via the API — clones are normal pages'
        AFTER `status`;

-- Template 1: "Coupon spotlight" — coupon merchandising layout.
-- Headline -> claimable coupon grid -> how-it-works -> deal rail -> fine print.
INSERT INTO `litemall_page` (`name`, `position`, `category`, `config`, `status`, `is_template`, `add_time`, `update_time`)
VALUES ('Coupon spotlight', 'custom', 'coupon',
'{"version":1,"components":[{"type":"rich-text","key":"hero","config":{"html":"<div><h1>Coupon Spotlight</h1><p>Fresh savings, updated all the time. Claim a coupon below, add eligible items to your cart, and the discount applies at checkout &mdash; no codes to remember.</p></div>"}},{"type":"coupon-strip","key":"claim-grid","config":{"headline":"Claim yours before they are gone","style":"grid","limit":6}},{"type":"rich-text","key":"how-it-works","config":{"html":"<div><h2>How it works</h2><ul><li><strong>Claim</strong> &mdash; tap a coupon above to add it to your account.</li><li><strong>Shop</strong> &mdash; add qualifying items to your cart.</li><li><strong>Save</strong> &mdash; pick the coupon at checkout and watch the total drop.</li></ul></div>"}},{"type":"goods-list","key":"deal-pairings","config":{"mode":"deals","limit":8,"title":"Deals worth pairing with a coupon"}},{"type":"rich-text","key":"fine-print","config":{"html":"<p><em>Coupons apply within their stated window and scope; one coupon per order. Full conditions are shown on each coupon.</em></p>"}}]}',
'draft', 1, NOW(), NOW());

-- Template 2: "Group-buy rally" — groupon merchandising layout.
-- Headline -> active-rally strip -> three-step explainer -> deal rail -> reassurance.
INSERT INTO `litemall_page` (`name`, `position`, `category`, `config`, `status`, `is_template`, `add_time`, `update_time`)
VALUES ('Group-buy rally', 'custom', 'groupon',
'{"version":1,"components":[{"type":"rich-text","key":"hero","config":{"html":"<div><h1>Group-Buy Rally</h1><p>Better prices when friends shop together. Join a rally below, share it with your crew, and everyone unlocks the team price when the group fills up.</p></div>"}},{"type":"groupon-strip","key":"active-rallies","config":{"title":"Rallies filling up right now","maxItems":6}},{"type":"rich-text","key":"steps","config":{"html":"<div><h2>Three steps to the team price</h2><ul><li><strong>1. Start or join</strong> &mdash; open a rally from the list above.</li><li><strong>2. Rally your crew</strong> &mdash; share the product with friends before the window closes.</li><li><strong>3. Everybody saves</strong> &mdash; when the group fills, every member pays the group price.</li></ul></div>"}},{"type":"goods-list","key":"more-deals","config":{"mode":"deals","limit":8,"title":"More deals while your group fills"}},{"type":"rich-text","key":"reassurance","config":{"html":"<p><em>A group that does not fill in time is simply refunded &mdash; there is no risk in starting one.</em></p>"}}]}',
'draft', 1, NOW(), NOW());
