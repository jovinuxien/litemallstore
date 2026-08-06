-- V51__coupon_discount_type.sql
--
-- Wave 18 coupon merchandising Phase 1 (promotion-service).
--
-- Percent-capable coupons: discount_type selects how litemall_coupon.discount
-- is interpreted — 0 (flat, default) keeps the existing "discount = absolute
-- amount off" semantics for every pre-V51 row; 1 (percent) makes discount the
-- percentage rate (validated 1-90 promotion-side). discount_cap optionally
-- bounds the absolute amount a percent coupon can take off (NULL = uncapped);
-- it is meaningless for flat coupons and left NULL there.
--
-- The computed effective discount is always resolved server-side by
-- promotion-service (usable/selectlist/redeem), so order-side money math is
-- unchanged by this migration.

ALTER TABLE litemall_coupon
    ADD COLUMN discount_type smallint NOT NULL DEFAULT 0
        COMMENT 'Wave 18: 0 = flat amount off (discount = amount), 1 = percent off (discount = rate 1-90)'
        AFTER discount,
    ADD COLUMN discount_cap decimal(10,2) NULL DEFAULT NULL
        COMMENT 'Wave 18: max absolute discount for percent coupons; NULL = uncapped'
        AFTER discount_type;
