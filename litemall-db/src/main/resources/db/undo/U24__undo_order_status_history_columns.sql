-- Undo V24 — drop the structured status-code columns from the history table.
ALTER TABLE `litemall_order_status`
  DROP COLUMN `new_status`,
  DROP COLUMN `old_status`;
