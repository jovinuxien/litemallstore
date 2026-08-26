-- Seasonal candidacy (2026-08-26): seasonal fitness as a scored, indexed signal.
--
-- The season page shipped in Wave 27 (V63) as a DIY page whose only rail is
-- mode=byIds with 24 hand-picked ids — the validator's maximum. That list is
-- frozen: a better new arrival cannot enter it and a sold-out product cannot
-- leave. These two tables make seasonal membership something the catalogue
-- computes instead of something a person retypes.
--
-- Every season is scored continuously, not just the running one, so the next
-- season's page is populated before anyone activates it. Membership rides the
-- search index as a MULTI-VALUED `seasons` field (mirroring category_names);
-- the per-season score stays here, because one scalar cannot honestly hold a
-- product's score for two different seasons.
--
-- Guarded-upsert semantics are identical to litemall_deal_candidate (V45) and
-- litemall_promo_candidate (V53): a re-run never overwrites an admin decision.

CREATE TABLE litemall_season_rule (
    id INT(11) NOT NULL AUTO_INCREMENT,
    season_key VARCHAR(31) NOT NULL COMMENT 'autumn | winter | spring | summer — and anything else later; nothing caps this at four',
    name VARCHAR(63) NOT NULL COMMENT 'display name',
    window_start_md VARCHAR(5) NOT NULL COMMENT 'MM-DD, inclusive; a window may wrap the year end (winter)',
    window_end_md VARCHAR(5) NOT NULL COMMENT 'MM-DD, inclusive',
    terms VARCHAR(2047) NOT NULL DEFAULT '[]' COMMENT 'matching terms, JSON array of strings; run through OCS to discover candidates',
    category_ids VARCHAR(1023) NOT NULL DEFAULT '[]' COMMENT 'boosted category ids, JSON array of ints',
    price_min DECIMAL(10,2) NULL COMMENT 'target retail band, low end; NULL = no bound',
    price_max DECIMAL(10,2) NULL COMMENT 'target retail band, high end; NULL = no bound',
    weights VARCHAR(1023) NOT NULL DEFAULT '{}' COMMENT 'JSON: euMultiplier, freshnessBonusMax, categoryBoost, demandWeight',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'per-season kill-switch',
    add_time DATETIME NULL DEFAULT NULL,
    update_time DATETIME NULL DEFAULT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_season_rule_key (season_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='season definitions as data: terms, boosted categories, price band and weights. Adding a season is a row, never a code edit.';

CREATE TABLE litemall_season_candidate (
    id INT(11) NOT NULL AUTO_INCREMENT,
    season_key VARCHAR(31) NOT NULL,
    goods_id INT(11) NOT NULL,
    day DATE NOT NULL COMMENT 'scoring day',
    tier VARCHAR(16) NOT NULL COMMENT 'hot | featured | watch',
    score DECIMAL(8,2) NOT NULL DEFAULT 0.00,
    reasons VARCHAR(1023) NULL COMMENT 'human-readable scoring reasons, JSON array of strings',
    status VARCHAR(16) NOT NULL DEFAULT 'proposed' COMMENT 'auto = published into the index | proposed = scored but under the tier/cap bar | dismissed = permanent admin veto',
    config_version_hash VARCHAR(64) NULL COMMENT 'fingerprint of the rule that scored this row, for grouping',
    config_snapshot VARCHAR(1023) NULL COMMENT 'the effective weights used, JSON. Kept BESIDE the hash on purpose: a hash alone resolves to nothing once the rule is edited or deleted.',
    add_time DATETIME NULL DEFAULT NULL,
    update_time DATETIME NULL DEFAULT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_season_candidate_season_goods_day (season_key, goods_id, day),
    KEY idx_season_candidate_season_day_status (season_key, day, status),
    KEY idx_season_candidate_goods (goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='scored seasonal proposals; status auto rows are what the index publishes as `seasons`';

-- The four northern-hemisphere seasons — correct for a DE/FR/DK/SE market.
-- Weights start deliberately mild: euMultiplier is highest where the season
-- carries a delivery deadline (winter gifting), and freshnessBonusMax is a
-- BONUS ceiling, never a decay, so a cold-start product is un-boosted rather
-- than zeroed (the score is multiplicative).
INSERT INTO litemall_season_rule
    (season_key, name, window_start_md, window_end_md, terms, category_ids, price_min, price_max, weights, enabled, add_time, update_time)
VALUES
    ('autumn', 'Autumn', '09-01', '11-30',
     '["autumn","fall","cosy","cozy","blanket","throw","candle","harvest","pumpkin","warm","knit","rug","lamp","curtain"]',
     '[]', 5.00, 500.00,
     '{"euMultiplier":1.20,"freshnessBonusMax":1.25,"categoryBoost":1.15,"demandWeight":1.00}', 1, NOW(), NOW()),
    ('winter', 'Winter', '12-01', '02-28',
     '["winter","christmas","xmas","gift","festive","snow","heater","thermal","fleece","advent","decoration","fairy lights"]',
     '[]', 5.00, 500.00,
     '{"euMultiplier":1.40,"freshnessBonusMax":1.25,"categoryBoost":1.15,"demandWeight":1.00}', 1, NOW(), NOW()),
    ('spring', 'Spring', '03-01', '05-31',
     '["spring","garden","planter","seed","easter","cleaning","storage","outdoor","patio","flower","pastel"]',
     '[]', 5.00, 500.00,
     '{"euMultiplier":1.10,"freshnessBonusMax":1.25,"categoryBoost":1.15,"demandWeight":1.00}', 1, NOW(), NOW()),
    ('summer', 'Summer', '06-01', '08-31',
     '["summer","bbq","barbecue","garden","picnic","cooling","fan","outdoor","beach","parasol","hose","camping"]',
     '[]', 5.00, 500.00,
     '{"euMultiplier":1.05,"freshnessBonusMax":1.25,"categoryBoost":1.15,"demandWeight":1.00}', 1, NOW(), NOW());
