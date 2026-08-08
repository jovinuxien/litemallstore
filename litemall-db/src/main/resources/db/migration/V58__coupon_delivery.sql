-- V58__coupon_delivery.sql
--
-- Wave 22 (2026-08-08): targeted coupon delivery (coupon roadmap Phase 4).
--
-- One row per admin "deliver to segment" run against a coupon:
-- segment_json records the RFM-style criteria the admin asked for
-- (recencyDays / minFrequency / minMonetary over PAID orders — house
-- paid-or-later convention order_status >= 201, as in StatMapper /
-- InsightMapper); matched / granted / skipped record the sweep outcome
-- (skipped = users already at the coupon's per-user claim limit or refused
-- by the grant path). Preview runs write NOTHING here — zero side effects.

CREATE TABLE litemall_coupon_delivery (
    id INT(11) NOT NULL AUTO_INCREMENT,
    coupon_id INT(11) NOT NULL,
    segment_json VARCHAR(511) NULL COMMENT 'delivery criteria as a JSON object, e.g. {"recencyDays":30,"minFrequency":2,"minMonetary":50}',
    matched INT(11) NOT NULL DEFAULT 0 COMMENT 'users matching the segment at delivery time',
    granted INT(11) NOT NULL DEFAULT 0 COMMENT 'coupons actually granted by the sweep',
    skipped INT(11) NOT NULL DEFAULT 0 COMMENT 'matched users skipped (already at per-user claim limit, or grant refused)',
    add_time DATETIME NULL DEFAULT NULL,
    update_time DATETIME NULL DEFAULT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_coupon_delivery_coupon_id (coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='targeted coupon delivery runs (Wave 22): segment criteria + sweep outcome';
