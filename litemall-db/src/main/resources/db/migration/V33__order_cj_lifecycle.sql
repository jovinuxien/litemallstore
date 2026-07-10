-- Wave 3 (order): CJ order lifecycle parity.
-- Last CJ-side order status seen by the status-sync poll (CjLifecycleService):
-- CREATED / IN_CART / UNPAID / UNSHIPPED / SHIPPED / DELIVERED / CANCELLED.
-- Drives confirm/payBalance retry, dedupes timeline hops (a hop is recorded only when
-- this value changes), and gates deleteOrder (only CREATED/IN_CART/UNPAID are deletable
-- at CJ). NULL for local orders and for CJ orders placed before this migration.
ALTER TABLE litemall_order
    ADD COLUMN cj_order_status VARCHAR(32) NULL DEFAULT NULL
        COMMENT 'last CJ order status seen by lifecycle sync (source=cj only)'
        AFTER cj_order_num;
