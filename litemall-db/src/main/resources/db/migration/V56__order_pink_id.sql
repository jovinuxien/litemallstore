-- V56__order_pink_id.sql
--
-- Wave 21 (transactional group-buy): link an order to the combination group-buy
-- slot ("pink", litemall_combination_pink.id, promotion-owned V30 table) it was
-- placed against. Written at submit when the customer checks out a group-buy
-- slot at the campaign's combinationPrice; the GROUP_EXPIRED listener uses the
-- key to find paid orders that must be auto-cancelled + refunded when the group
-- fails, and the cancel paths use it to release the slot at promotion.
--
-- Nullable: absent = a plain (non-group) order, existing flows unchanged. The
-- legacy litemall_groupon path (groupon_price / litemall_groupon linkage) is a
-- separate, older flow and is untouched by this column.

ALTER TABLE litemall_order
    ADD COLUMN pink_id INT NULL
        COMMENT 'combination group-buy slot (litemall_combination_pink.id) this order was placed against; NULL = not a group-buy order'
        AFTER cj_logistic_name,
    ADD KEY idx_order_pink_id (pink_id);
