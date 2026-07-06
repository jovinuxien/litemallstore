# Plan — refund must mirror the tender, capped at the captured amount

**Worktree:** `order` (`fix/order`) · **Status:** IMPLEMENTED 2026-07-03 (approved; §5
resolved to the default — **(A) `pay_id` encoding + ledger cap**, no migration).
Tests: `LitemallOrderRefundTenderTest` (6 cases: card-no-wallet-credit, wallet-exact,
cap, idempotent replay, legacy-no-capture, legacy-ledger-fallback) — all green alongside
the repaired `LitemallOrderPaidCancelTest` and the lifecycle state-machine suite (24/24).
Note: the tender is `PaymentMethod.name()` (`CREDIT_CARD`/`DEBIT_CARD`/… — there is no
literal `CARD` constant), so `pay_id` reads e.g. `CREDIT_CARD:pi_123`.
**Scope:** `litemall-order/` only.

## 1. Problem — a refund can exceed what was actually paid

Payment is **tender-aware**, refund is **tender-blind**:

- **Pay** (`LitemallOrderOrchestratorService.handlePaymentAction:208`) debits the wallet
  **only** when `paymentMethod == WALLET`. CARD/digital records a client-confirmed
  Stripe reference and does **not** touch the wallet (`processPayment:289`).
- **Refund** (`approveRefund:623` → `creditWalletForRefund:664`) **always** credits the
  buyer's **wallet** with the full `order.getActualPrice()`, regardless of how the order
  was paid and without checking the captured amount.

Resulting defects:

1. **Cross-tender refund (live bug).** A CARD-paid order never debited the wallet, yet
   refund credits the wallet the whole `actualPrice` (and never reverses the card charge).
   The buyer gains the full order amount in wallet money they never funded.
2. **No capture cap (policy/correctness).** Refund = `actualPrice` = goods + freight −
   coupon, hard-coded. There is no `min(actualPrice, captured)` and no
   item-only-vs-with-shipping policy, so a return refunds shipping too.

**Not a live bug (already mitigated — do not "fix" again):** the credit happens before the
guarded status flip, but the orchestrator is **class-level `@Transactional`**
(`LitemallOrderOrchestratorService:48`). A concurrent second approval reaches
`refundOrder` → `markRefundedIfRequested` (guarded `UPDATE ... WHERE order_status =
REFUND_REQUEST`), flips **0 rows**, throws `IllegalStateException`, and the whole
transaction — including its wallet credit — rolls back. We keep the credit-after-flip
ordering for clarity + an idempotency key as defense in depth (§3.3), but the TOCTOU is
not the reproducible defect.

## 2. Decision — refund the same tender that paid, capped at capture

A refund returns money **to the channel it came from**, never more than was captured:

| Paid via | Refund destination | Amount |
|----------|-------------------|--------|
| WALLET   | wallet credit (existing path) | the recorded wallet debit for the order (= `actualPrice` today) |
| CARD / digital | Stripe reversal (documented stub today — emit event / log, **no wallet credit**) | the captured charge, capped at `actualPrice` |
| (no capture found) | nothing | 0 — log + return a clean "nothing to refund" result |

We need two things the code lacks: **(a)** the tender used, and **(b)** the captured
amount. Both come from one source of truth we already half-have — the **wallet ledger**
(`litemall_user_bill`), plus the order's own pay metadata.

### Tender source — recommended: persist `PaymentMethod` at pay time
The order row has **no payment-method column** (`pay_id` is a free-form string; there is a
`refund_type` column). Recommended: persist the selected `PaymentMethod` on the order when
it is marked paid, so refund routing is explicit and self-contained.

- **Option A (recommended): store the method on the order.** Encode it in the existing
  `pay_id` (e.g. `WALLET` / `CARD:<stripePaymentIntentId>`) set in `markOrderPaid`, OR add
  a dedicated `payment_method` column via a new Flyway migration **V26** (mirrors the V24
  pattern this worktree already added). A column is cleaner; `pay_id` avoids a migration.
- **Option B (no schema change): derive from the ledger.** It was wallet-paid **iff** a
  wallet debit bill exists for the order (`relatedType=ORDER`, `relatedRef=orderId`,
  `kind=PAYMENT`). Captured amount = that bill's amount. Needs a new bill lookup (§3.2).

Recommendation: **Option A (pay_id encoding) for routing + Option B's ledger lookup for the
capture amount/cap.** The ledger is the authoritative "what was actually taken"; the order
field is the authoritative "by what method". Using both keeps routing explicit and the
amount honest.

## 3. Changes (all under `litemall-order/`)

### 3.1 Persist the tender at payment (`LitemallOrderServiceImpl.markOrderPaid` / `handlePaymentAction`)
- When marking paid, record the method on the order (`pay_id = WALLET` or
  `CARD:<reference>`), within the existing pay transaction.
- If choosing the column route instead: add **V26 `__order_payment_method.sql`** (+ undo),
  add `paymentMethod` to `LitemallOrderAggregate` and the row mapping, set it in
  `markOrderPaid`.

### 3.2 Tender-aware, capped refund (`approveRefund` + `creditWalletForRefund`)
Replace the unconditional wallet credit with a router:
```
PaymentMethod tender = order.paidTender();              // from pay_id / column
LitemallMoney captured = walletLedger.capturedForOrder(order);  // wallet debit for this order, else null
LitemallMoney refund   = min(order.getActualPrice(), captured-or-actualPrice);  // never exceed capture
switch (tender) {
  case WALLET -> creditWalletForRefund(order, orderId, refund);   // credit the *capped* amount
  case CARD, ALIPAY, WECHAT, ... -> reverseExternalCharge(order, refund);  // Stripe-reversal stub: emit
                                                                            // LitemallOrderRefundedEvent +
                                                                            // log; NO wallet credit
}
orderServiceImpl.refundOrder(orderId, refund);          // persist refund_amount = the capped, real amount
```
- `creditWalletForRefund` takes the **amount** as a parameter (stop reading
  `actualPrice` internally) so the cap is honored.
- `refundOrder(orderId, refund)` already persists `refund_amount` via the guarded
  `markRefundedIfRequested` — feed it the capped amount so the stored `refund_amount`
  equals what was actually returned.
- New `LitemallBillRepository.findOrderPaymentDebit(userId, orderId)` (or
  `capturedForOrder`) returns the wallet debit recorded at pay time; impl filters the
  bill ledger by the `relatedType=ORDER / relatedRef=orderId / kind=PAYMENT` keys the
  debit command already writes (`debitWalletForOrder:325-332`).

### 3.3 Idempotent refund credit (defense in depth)
- Give the refund credit a deterministic business key (it already carries
  `"ORDER"/"REFUND"/orderId` — `creditWalletForRefund:669-676`). Make the wallet credit a
  no-op when a credit with that key already exists, so a retry/replay cannot double-credit
  even outside the guarded transition.

### 3.4 Keep the CARD/Stripe boundary documented
- The CARD branch stays a **seam**: emit `LitemallOrderRefundedEvent` (already exists) +
  log "would reverse Stripe charge <ref> for <amount>", and do **not** credit the wallet.
  Update `docs/handoff-gateway-api-order-timeline.md` §4 to state refund-to-tender and the
  capture cap (supersedes the "refund always credits the wallet" wording).

## 4. Acceptance

- **WALLET path unchanged in the happy case:** pay $X by wallet → refund credits the wallet
  exactly $X (the recorded debit), `refund_amount = X`. Wallet net movement is zero.
- **CARD path no longer credits the wallet:** pay by CARD (no wallet debit) → approve refund
  → wallet balance **unchanged**; a refund event/log is emitted for the PSP reversal;
  `refund_amount` reflects the captured card amount, not a wallet credit.
- **Cap holds:** `refund_amount <= captured` for every refunded order; no order ends with
  `refund_amount > actual_price`.
- **Idempotency:** approving/replaying a refund twice credits the wallet at most once.
- `mvn -q -o -pl litemall-order -am compile` clean; the lifecycle state-machine unit test
  still passes; add one unit/integration check: CARD-paid order → approveRefund → no wallet
  credit + `refund_amount` set.

## 5. Open question for the approver

Pick the tender-persistence route: **(A) encode in `pay_id`** (no migration, recommended)
or **(A′) dedicated `payment_method` column via V26** (cleaner, one migration). Either way
the captured **amount** is read from the wallet ledger (Option B lookup). Default if no
preference: **(A) `pay_id` encoding + ledger cap**.
