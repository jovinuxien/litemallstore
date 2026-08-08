-- V52__order_cj_logistic_choice.sql
--
-- Delivery-option chooser at checkout: the customer may pick one of the CJ
-- logistics lines that freightCalculate offers for their cart + destination
-- (checkout shows them via POST /srv/order/freight-quote `cj.options`). The
-- pick is captured at submit and honored at pay-time CJ placement WHEN CJ
-- still offers that line for the shipment; otherwise placement falls back to
-- the configured default / cheapest exactly as before (createOrder rejects a
-- logisticName freightCalculate does not offer — code 1605001).
--
-- Distinct from ship_channel, which records the carrier actually used once
-- the parcel ships. Nullable: absent = no explicit choice, existing flows
-- unchanged.

ALTER TABLE litemall_order
    ADD COLUMN cj_logistic_name VARCHAR(64) NULL
        COMMENT 'CJ logistics line chosen by the customer at checkout (used at placement when still offered)'
        AFTER cj_order_num;
