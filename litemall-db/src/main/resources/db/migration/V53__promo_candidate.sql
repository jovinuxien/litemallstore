-- Wave 19 (2026-08-08): promo candidate intelligence.
-- Scored coupon/groupon proposals over the Wave-12/14 inventory data. Admin
-- consumes a proposal by creating the real coupon/combination through the
-- existing promotion admin paths (the insight endpoint then flips the row),
-- or dismisses it. A re-run of the scorer never overwrites a decided row
-- (same guarded-upsert semantics as litemall_deal_candidate, V45).

CREATE TABLE litemall_promo_candidate (
    id INT(11) NOT NULL AUTO_INCREMENT,
    kind VARCHAR(16) NOT NULL COMMENT 'coupon | groupon',
    goods_id INT(11) NOT NULL,
    day DATE NOT NULL COMMENT 'scoring day',
    tier VARCHAR(16) NOT NULL COMMENT 'hot | featured | watch',
    score DECIMAL(8,2) NOT NULL DEFAULT 0.00,
    suggestion VARCHAR(1023) NULL COMMENT 'kind-specific suggested values, JSON object; coupon suggestions are pre-validated against the Wave-18 margin-guard formula',
    reasons VARCHAR(1023) NULL COMMENT 'human-readable scoring reasons, JSON array of strings',
    status VARCHAR(16) NOT NULL DEFAULT 'proposed' COMMENT 'proposed | dismissed | consumed',
    ref_id INT(11) NULL COMMENT 'created coupon/combination id, set on consume',
    add_time DATETIME NULL DEFAULT NULL,
    update_time DATETIME NULL DEFAULT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_promo_candidate_kind_goods_day (kind, goods_id, day),
    KEY idx_promo_candidate_kind_day_status (kind, day, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='scored coupon/groupon merchandising proposals (Wave 19); admin consumes or dismisses';
