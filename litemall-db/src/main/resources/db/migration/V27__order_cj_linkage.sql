-- V27__order_cj_linkage.sql
--
-- Make CJ Dropshipping orders first-class local orders so they appear in the
-- customer "My Orders" list/detail/timeline exactly like standard orders, and
-- so payment precedes CJ placement (pay-first flow):
--
--   * source        -> 'local' (native fulfillment) vs 'cj' (fulfilled via CJ
--                      createOrder after payment succeeds). Mirrors the
--                      litemall_goods.source discriminator from V23.
--   * address_id    -> the shipping address the order was submitted with. CJ
--                      createOrder needs the STRUCTURED address (province/city/
--                      zip as separate fields) which the flattened
--                      litemall_order.address string cannot reproduce, so the
--                      pay-time CJ placement re-resolves it from litemall_address.
--   * cj_order_id / cj_order_num -> CJ's identifiers, written once CJ accepts
--                      the order. order_sn is replayed to CJ as the merchant
--                      orderNumber (idempotency key), so a NULL cj_order_id on a
--                      PAID source=cj order marks an unconfirmed placement.
--
-- All columns nullable / defaulted: existing inserts keep working unchanged and
-- historical rows read as source='local'.

ALTER TABLE litemall_order
    ADD COLUMN address_id   INT NULL
        COMMENT 'litemall_address.id the order was submitted with (structured address for CJ placement)',
    ADD COLUMN country_code VARCHAR(8) NULL
        COMMENT 'ISO destination country picked at checkout (litemall_address has no country; CJ createOrder needs one)',
    ADD COLUMN source       VARCHAR(32) NOT NULL DEFAULT 'local'
        COMMENT 'fulfillment origin of the order: local | cj',
    ADD COLUMN cj_order_id  VARCHAR(64) NULL
        COMMENT 'CJ Dropshipping order id returned by createOrder when source=cj',
    ADD COLUMN cj_order_num VARCHAR(64) NULL
        COMMENT 'CJ Dropshipping order number returned by createOrder when source=cj';
