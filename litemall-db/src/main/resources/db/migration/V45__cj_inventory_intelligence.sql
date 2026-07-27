-- V45__cj_inventory_intelligence.sql
--
-- CJ inventory intelligence (Wave 12, goods-management).
--
-- 1) Cost capture. The raw CJ wholesale price (CJProduct.sellPrice, USD) was
--    discarded at sync — only the marked-up retail survived, so margin was
--    invisible downstream. sell_price lands the range's LOWER BOUND per product;
--    per-variant costs ride variants_json (variant_sell_price, no schema change).
--    The pre-existing V2 cost columns on litemall_goods / litemall_goods_product
--    (NOT NULL DEFAULT 0.00) become the promoted landing spots — a cost of 0.00
--    still means "not captured yet" and must render as NULL margin, never 0%.
--
-- 2) Sync-run bookkeeping. Sync/enrich/flow outcomes were a single log line;
--    litemall_cj_sync_run makes each phase's counts and failures queryable
--    (wire-tap bookkeeping of the Wave-12 inventory flow).
--
-- 3) Per-product daily time series. litemall_product_metric_daily records one
--    idempotent row per goods per day (price/cost/margin, stock, availability,
--    views, sales) from the product's arrival date onward — the /insight series.
--
-- 4) Deal proposals. Daily new arrivals are scored into litemall_deal_candidate
--    (proposed | approved | dismissed); deals are ONLY created when an admin
--    approves a proposal — never automatically.
--
-- All columns are defaulted/nullable so existing inserts keep working unchanged.

ALTER TABLE litemall_cj_product
    ADD COLUMN sell_price DECIMAL(10,2) NULL
        COMMENT 'raw CJ wholesale price, USD (range lower bound); cost basis for retail = sell_price x margin';

CREATE TABLE litemall_cj_sync_run (
    id INT(11) NOT NULL AUTO_INCREMENT,
    phase VARCHAR(16) NOT NULL COMMENT 'sync | enrich | flow',
    started_time DATETIME NOT NULL,
    finished_time DATETIME NULL,
    upserted INT(11) NOT NULL DEFAULT 0,
    inserted INT(11) NOT NULL DEFAULT 0,
    updated INT(11) NOT NULL DEFAULT 0,
    removed INT(11) NOT NULL DEFAULT 0,
    complete TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1 = phase ran to completion; 0 = failed or aborted (see error)',
    error VARCHAR(1023) NULL COMMENT 'failure detail when complete = 0',
    add_time DATETIME NULL DEFAULT NULL,
    update_time DATETIME NULL DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_sync_run_phase_started (phase, started_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CJ catalog pipeline run bookkeeping (Wave 12)';

CREATE TABLE litemall_product_metric_daily (
    goods_id INT(11) NOT NULL,
    day DATE NOT NULL,
    retail_price DECIMAL(10,2) NULL,
    cost DECIMAL(10,2) NULL COMMENT 'captured CJ wholesale (USD); NULL when not yet captured',
    margin_pct DECIMAL(6,2) NULL COMMENT '(retail - cost) / retail * 100; NULL when cost is unknown',
    stock_total INT(11) NULL COMMENT 'sum of SKU stock at record time',
    available TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1 = pid live at CJ; 0 = vanished from the feed',
    views INT(11) NOT NULL DEFAULT 0,
    sales_qty INT(11) NOT NULL DEFAULT 0,
    update_time DATETIME NULL DEFAULT NULL,
    PRIMARY KEY (goods_id, day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='per-goods daily inventory/margin/engagement series (Wave 12); upsert is idempotent per (goods_id, day)';

CREATE TABLE litemall_deal_candidate (
    id INT(11) NOT NULL AUTO_INCREMENT,
    goods_id INT(11) NOT NULL,
    day DATE NOT NULL COMMENT 'arrival day the proposal was scored for',
    tier VARCHAR(16) NOT NULL COMMENT 'deal tier label (e.g. hot | featured | watch)',
    score DECIMAL(8,2) NOT NULL DEFAULT 0.00,
    suggested_deal_price DECIMAL(10,2) NULL,
    reasons VARCHAR(1023) NULL COMMENT 'human-readable scoring reasons, JSON array of strings',
    status VARCHAR(16) NOT NULL DEFAULT 'proposed' COMMENT 'proposed | approved | dismissed',
    add_time DATETIME NULL DEFAULT NULL,
    update_time DATETIME NULL DEFAULT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_deal_candidate_goods_day (goods_id, day),
    KEY idx_deal_candidate_day_status (day, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='scored flash-deal proposals for daily CJ arrivals (Wave 12); admin approves/dismisses';
