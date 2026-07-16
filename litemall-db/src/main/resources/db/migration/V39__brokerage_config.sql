-- Wave 5 (affiliate program): brokerage engine configuration seed.
--
-- The ONLY schema work this wave: four litemall_system rows read per-event by
-- order's BrokerageService (kill switch, flat global rate, freeze window,
-- withdrawal floor). No new tables — V7 already created
-- litemall_user_brokerage_record and litemall_user carries the spread_* /
-- is_promoter / brokerage_price columns.
--
-- Rows MUST exist up front: LitemallSystemConfigService.updateConfig is an
-- UPDATE-by-key that silently no-ops on unknown keys, so the admin config form
-- (gateway-admin Sys/ConfigBrokerage) can never create these.

INSERT INTO `litemall_system` (`key_name`, `key_value`, `add_time`, `update_time`, `deleted`) VALUES
('litemall_brokerage_enabled', 'false', NOW(), NOW(), 0),
('litemall_brokerage_rate', '5', NOW(), NOW(), 0),
('litemall_brokerage_freeze_days', '7', NOW(), NOW(), 0),
('litemall_brokerage_min_extract', '10', NOW(), NOW(), 0);
