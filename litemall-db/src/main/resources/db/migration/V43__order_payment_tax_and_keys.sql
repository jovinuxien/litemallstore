-- =============================================================================
-- V43 — Money-path hardening: Stripe payment reference, tax, and the integrity
-- keys the order table never had (Wave 7, order worktree).
--
-- Backs: server-side PaymentIntent verification + replay defence, the Stripe
-- webhook's idempotency, US sales tax / EU VAT at checkout, and the uniqueness
-- that generateOrderSn only ever checked in application code.
--
-- V42 (promotion social_post) is the last used; V40 stays earmarked for
-- goods-management's parked CJ-deals work. Checked against flyway_schema_history
-- and every sibling worktree immediately before writing this file.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Stripe payment reference.
--
-- pay_id already exists but cannot carry this: it is an overloaded tender label
-- holding 'WALLET' (18 rows in dev share that literal), 'CREDIT_CARD:pi_...' and
-- 'OFFLINE:...', so it can never be UNIQUE. A dedicated column gives the DB —
-- not application code — the final say on PaymentIntent replay across orders.
-- NULL for wallet/offline tenders; MySQL permits many NULLs under a UNIQUE index,
-- so those rows never collide.
-- -----------------------------------------------------------------------------
ALTER TABLE `litemall_order`
  ADD COLUMN `payment_intent_id` varchar(63) DEFAULT NULL
      COMMENT 'Stripe PaymentIntent id (pi_...), verified server-side; NULL for wallet/offline tenders';

ALTER TABLE `litemall_order`
  ADD UNIQUE KEY `uk_order_payment_intent_id` (`payment_intent_id`);

-- -----------------------------------------------------------------------------
-- 2. Tax (US sales tax + EU VAT).
--
-- NOT NULL DEFAULT 0.00 so every existing order reads as untaxed rather than
-- NULL — the money chain sums these and must never meet a NULL. tax_breakdown
-- keeps the provider's per-jurisdiction detail for invoices/audit; it is not
-- read back into the total.
-- -----------------------------------------------------------------------------
ALTER TABLE `litemall_order`
  ADD COLUMN `tax_price` decimal(10,2) NOT NULL DEFAULT 0.00
      COMMENT 'Tax collected at checkout (sales tax / VAT); 0.00 when tax is disabled',
  ADD COLUMN `tax_breakdown` text DEFAULT NULL
      COMMENT 'Provider tax breakdown JSON (per-jurisdiction lines) for invoices/audit; never summed';

-- -----------------------------------------------------------------------------
-- 3. Stripe webhook idempotency.
--
-- Stripe retries deliveries, so the same event.id can arrive many times. The
-- UNIQUE key is the dedupe: an insert that fails on it means "already handled"
-- and the handler returns 200 without re-processing. Deliberately NOT a soft-
-- deleted table — a purged row would let an old event replay.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `litemall_stripe_event` (
  `id`          int(11) NOT NULL AUTO_INCREMENT,
  `event_id`    varchar(63) NOT NULL              COMMENT 'Stripe event id (evt_...); the idempotency key',
  `event_type`  varchar(63) NOT NULL              COMMENT 'e.g. payment_intent.succeeded | payment_intent.payment_failed',
  `order_id`    int(11) DEFAULT NULL              COMMENT 'Resolved litemall_order.id, when the event mapped to one',
  `add_time`    datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stripe_event_id` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Processed Stripe webhook events (idempotency ledger, Wave 7)';

-- -----------------------------------------------------------------------------
-- 4. order_sn uniqueness.
--
-- generateOrderSn() loops until countByOrderSn() returns 0, but that count is
-- scoped per-user AND per-not-deleted while reconciliation — and CJ's dedupe on
-- the merchant orderNumber — are global. Two users could legitimately hold the
-- same sn on the same day, and CJ would treat the second order as a duplicate of
-- the first. Let the DB enforce what the loop only approximated.
-- Verified zero duplicates in dev before adding this.
-- -----------------------------------------------------------------------------
ALTER TABLE `litemall_order`
  ADD UNIQUE KEY `uk_order_order_sn` (`order_sn`);

-- -----------------------------------------------------------------------------
-- 5. The user_id indexes both hot tables were missing.
-- Every cart read and every "my orders" page filters on user_id.
-- -----------------------------------------------------------------------------
ALTER TABLE `litemall_order`
  ADD KEY `idx_order_user_id` (`user_id`);

ALTER TABLE `litemall_cart`
  ADD KEY `idx_cart_user_id` (`user_id`);
