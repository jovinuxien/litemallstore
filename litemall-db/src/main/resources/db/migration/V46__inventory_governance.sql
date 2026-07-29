-- V46__inventory_governance.sql
--
-- Inventory governance (Wave 14, goods-management).
--
-- 1) Retirement proposals. The catalog is governed toward a target size
--    (litemall.inventoryflow.catalog-target); the inventory flow scores weak
--    on-sale goods (unavailable streak, low stock, weak/null margin, dead
--    engagement) into litemall_retire_candidate. Retirement = OFF-SALE
--    (reversible; the goods stays viewable-unbuyable). Proposals are only
--    executed after an admin approves them; a daily executor flips approved
--    batches whose execute_on has arrived.
--
-- 2) Per-category margin overrides. Retail = cost x margin; the global margin
--    (spring.cjdropship.pricing.margin, 1.25) can be overridden per L1 root
--    category via litemall_category_margin. Overrides take effect at the
--    nightly reprice sites. Hard-deleted on removal (no soft-delete column
--    by design; the row either exists and applies, or does not).
--
-- All columns are defaulted/nullable so existing inserts keep working unchanged.

CREATE TABLE litemall_retire_candidate (
    id INT(11) NOT NULL AUTO_INCREMENT,
    goods_id INT(11) NOT NULL,
    day DATE NOT NULL COMMENT 'day the proposal was scored',
    score DECIMAL(8,2) NOT NULL DEFAULT 0.00 COMMENT 'higher = retire sooner',
    reasons VARCHAR(1023) NULL COMMENT 'human-readable scoring reasons, JSON array of strings',
    status VARCHAR(16) NOT NULL DEFAULT 'proposed' COMMENT 'proposed | approved | dismissed | executed',
    execute_on DATE NULL COMMENT 'scheduled off-sale date for approved rows; executor runs batches with execute_on <= today',
    add_time DATETIME NULL DEFAULT NULL,
    update_time DATETIME NULL DEFAULT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_retire_candidate_goods_day (goods_id, day),
    KEY idx_retire_candidate_day_status (day, status),
    KEY idx_retire_candidate_status_execute (status, execute_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='scored retirement (off-sale) proposals for weak catalog goods (Wave 14); admin approves/dismisses, executor flips';

CREATE TABLE litemall_category_margin (
    category_id INT(11) NOT NULL COMMENT 'L1 root category id (litemall_category.pid = 0)',
    margin DECIMAL(4,2) NOT NULL COMMENT 'retail = cost x margin for goods under this root; bounds 1.05 - 3.0',
    add_time DATETIME NULL DEFAULT NULL,
    update_time DATETIME NULL DEFAULT NULL,
    PRIMARY KEY (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='per-L1-category margin override (Wave 14); absent row = global spring.cjdropship.pricing.margin';
