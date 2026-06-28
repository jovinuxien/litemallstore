-- =============================================================================
-- V24 — Add structured status codes to the order status-history audit table.
--
-- V11 created litemall_order_status as a change-log (change_type/change_message/
-- operator) but with no machine-readable status codes, so a timeline could not be
-- rendered deterministically. Add old_status / new_status (the LitemallOrderStatus
-- codes, e.g. 101→201) so the customer/admin timeline maps codes→labels without
-- parsing free text. Additive + idempotent.
-- Undo: db/undo/U24__undo_order_status_history_columns.sql
-- =============================================================================

ALTER TABLE `litemall_order_status`
  ADD COLUMN `old_status` smallint(6) DEFAULT NULL COMMENT '变更前订单状态码 (LitemallOrderStatus.code); null for the initial create row' AFTER `order_id`,
  ADD COLUMN `new_status` smallint(6) DEFAULT NULL COMMENT '变更后订单状态码 (LitemallOrderStatus.code)' AFTER `old_status`;
