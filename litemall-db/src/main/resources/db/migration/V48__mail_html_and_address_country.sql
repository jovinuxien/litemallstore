-- =============================================================================
-- V48 — HTML customer mail + address country (order-mail & checkout-regions wave).
-- 1) litemall_mail_outbox.body_html: optional rendered HTML alternative; the
--    sweep sends multipart (text + html) when present, plain text when NULL.
--    The plain-text `body` column stays authoritative for fallback and for the
--    admin outbox panel display.
-- 2) litemall_address.country_code: ISO-3166 alpha-2 destination country, the
--    address-book counterpart of litemall_order.country_code (V27). NULL on
--    legacy rows = country unknown.
-- 3) litemall_order.address widens 127 -> 255: the snapshot becomes the
--    structured "detail, city, province zip, Country" form with separators
--    and the postal code included, which no longer fits 127 reliably.
--
-- V47 (guest accounts / google identity) is the last applied migration —
-- verified against flyway_schema_history on 2026-08-02.
-- =============================================================================

ALTER TABLE `litemall_mail_outbox`
  ADD COLUMN `body_html` mediumtext NULL COMMENT 'Rendered HTML body (multipart alternative; NULL = plain-text-only mail)' AFTER `body`;

ALTER TABLE `litemall_address`
  ADD COLUMN `country_code` varchar(8) NULL COMMENT 'ISO-3166 alpha-2 destination country (matches litemall_order.country_code)' AFTER `postal_code`;

ALTER TABLE `litemall_order`
  MODIFY COLUMN `address` varchar(255) NOT NULL COMMENT 'Shipping address snapshot: detail, city, province zip, country (legacy rows: unseparated concat)';
