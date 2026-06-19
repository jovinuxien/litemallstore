# Follow-up → `order` worktree: expose the post-order payment action at `/srv/order/{orderId}/actions/pay`

**Owner of the fix:** `fix/order` worktree (order service owns the payment action).
**Raised by:** `fix/gateway-api` worktree, during live checkout verification (2026-06-19).
**Why this doc lives here:** discovered while wiring the customer SPA checkout payment step.
Per the `gateway-api` scope rule we do **not** add order/payment business logic in the SPA —
this is the precise spec for the missing REST entry point.

---

## Symptom (verified live, 2026-06-19)

The customer checkout lets the buyer pick a payment method (CARD / WALLET), but **there is no
way to actually pay a placed order**:

- `POST /srv/order/submit` (`LitemallPlaceOrderCommand`) has **no `paymentMethod`** field — it
  creates an unpaid order only. (The SPA `orderSlice` therefore keeps `paymentMethod` in UI
  state and does not send it.)
- `LitemallOrderRestController` (`/srv/order`) exposes only `/list`, `/submit`, and
  `/{orderId}/actions/cancel`. **There is no `/actions/pay` (or `/prepay`) endpoint**, so the
  order orchestrator's PAY path is unreachable over HTTP.

## The work is mostly done — only the REST layer is missing

The payment **domain + orchestration already exist** on `fix/order`; only the controller verb
is absent.

- `application/LitemallOrderOrchestratorService` already routes
  `OrderAction.PAY → handlePaymentAction(LitemallOrderPaymentCommand)` and it:
  - validates `OrderAction.PAY` is allowed for the order status (else `invalidStateTransition`);
  - for `PaymentMethod.WALLET`, debits via the wallet vertical
    (`LitemallWalletDomainService` → repository, emitting `LitemallWalletDebitedEvent`);
    **insufficient balance raises `LitemallInsufficientBalanceException` and rolls back the
    transaction — no paid order is produced** (the exact Task C acceptance behavior);
  - calls `processPayment(order, paymentInfo)` (the card/gateway seam), then `markOrderPaid`
    → `LitemallOrderStatus.PAID`, runs post-payment, and emits `LitemallOrderPaymentSuccessEvent`.
- `domain/model/commands/payment/LitemallOrderPaymentCommand` =
  `{ orderId, userId, paymentInfo: LitemallPaymentInfo, paymentMethod: PaymentMethod }`.
- `PaymentMethod` enum already includes `WALLET`, `CREDIT_CARD`, `DEBIT_CARD`, etc.
- Payment VOs exist: `LitemallPaymentInfo`, `LitemallPaymentCard`, `LitemallDigitalWallet`,
  `LitemallPaymentAmount`, `LitemallBillingAddress`.

## Required `order` changes

1. Add `POST /srv/order/{orderId}/actions/pay` to `LitemallOrderRestController`, **mirroring the
   existing `/{orderId}/actions/cancel`**:
   - `orderId` from the path; **`userId` from the gateway-injected `X-User-Id` header** (never
     from the body — same identity rule as submit/cancel);
   - body carries `paymentMethod` and the method-specific `LitemallPaymentInfo`
     (wallet: nothing extra / amount; card: token or `LitemallPaymentCard`);
   - build a `LitemallOrderPaymentCommand` and dispatch `OrderAction.PAY` through the
     orchestrator (do not re-implement payment in the controller);
   - return the standard `ApiResponse`/errno envelope: success → order is `PAID`; insufficient
     wallet balance → non-zero errno surfacing `LitemallInsufficientBalanceException`, order
     left unpaid.
2. **CARD / Stripe — decide and document the boundary.** The SPA uses `StripePaymentComponent`.
   Settle whether the order service charges Stripe server-side (needs a token in
   `LitemallPaymentInfo`) or records a client-confirmed Stripe result (SPA confirms, then calls
   `/actions/pay` to mark paid). `processPayment(...)` is currently a simplified seam — wire it
   to the chosen flow.

## SPA side (gateway-api follow-up, noted for sequencing — NOT order scope)

Once `/actions/pay` exists, the customer `Checkout` must, after a successful `/srv/order/submit`,
call `POST /srv/order/{orderId}/actions/pay` with the selected method before routing to
confirmation (WALLET inline; CARD via the Stripe step). That wiring lives in
`litemall-gateway-api` and is tracked in its `docs/SRV-FOLLOWUPS.md`.

## Cross-references
- `litemall-order/docs/handoff-gateway-api-srv-address.md` — the sibling address-book blocker for checkout.
- `litemall-order/docs/handoff-gateway-api-order-routing.md` — the order/cart/wallet contract.
- `litemall-gateway-api/docs/SRV-FOLLOWUPS.md` — the SPA-side record of this payment-action gap.
