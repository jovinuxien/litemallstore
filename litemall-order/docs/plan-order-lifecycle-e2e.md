# Plan — order lifecycle end to end (placement → payment → CJ → delivery → closure)

Status: **APPROVED 2026-09-05** (decisions D1–D6 all "yes, as recommended";
D6 = prod `CJ_OPS_MAIL` is a MAIN-session deploy step). Worktree `order`.
**Package A BUILT + MERGED to master `c9ec31257` 2026-09-05** (suite 342/0,
baseline 298; design in `adr-stripe-payments.md` §11).
**Packages B + C BUILT 2026-09-05** — suite **387 run / 0 failures** (+45 over
A). B: `CjFulfilmentIncidentService` (stall warn/park, lifecycle-failure
alerts, CJ-cancelled customer mail), parked rows in the pending list +
`POST …/cj-placement/requeue`, `[AFTERSALE_OPEN]`/`[PARKED]` refusals, sweep
holds on open aftersale, TRACKING_PENDING, sync predicate retires 401/402,
poller ships as `system`. C: `delivered` mail, auto-confirm days from the
system setting, `POST …/actions/refund/withdraw` + `handleOption.withdrawRefund`,
F18 helper clean-up. D: `handoff-gateway-admin-cj-requeue.md`,
`handoff-gateway-api-lifecycle.md`; goods-management raise (review purchase
check) recorded in CLAUDE.md. Dev boot verified after each package.
**DEPLOYED to trovemo.com 2026-09-05 02:39 UTC** — order container only (the
changed core classes are referenced only by litemall-order), `CJ_OPS_MAIL`
set, jar + env verified inside the container, smoke green. Live prod facts:
order 11 = F7 (approved-then-rejected, IOSS) now visible + requeueable;
orders 7/10 await the user's review; order 9 at 202. No migration.

## 0. Verdict

The lifecycle is complete on paper and has never been exercised beyond
"CJ order created" anywhere. Every stage exists in code, most of it is
careful, and the gaps are concentrated in four places:

1. **Money can be taken for an order the store has already cancelled.**
2. **After CJ placement the order goes dark**: local status never leaves
   201 until CJ says SHIPPED, and the three ways it can stall (CJ never
   paid from balance, CJ cancels, placement rejected after approval)
   are log-only or invisible in the admin panel.
3. **The customer is told almost nothing** between "paid" and "shipped",
   is never told the order closed, and cannot see the timeline the FAQ
   promises them.
4. **Nothing has ever run for real.** Dev has never produced a SHIPPED
   CJ order (only sandbox CREATED → UNPAID → UNSHIPPED); prod has never
   placed a CJ order and `litemall_mail_outbox` is empty in prod.

Evidence, verified by reading the code and the dev database on
2026-09-05, is in §1. The fix plan is §2–§5. Decisions the user must
make before I edit code are in §6.

## 1. Findings (verified)

Status codes: 101 CREATED · 102 CANCELED · 103 SYSTEM_CANCELED ·
201 PAID · 202 REFUND_REQUEST · 203 REFUNDED · 301 SHIPPED ·
401 DELIVERED · 402 AUTO_DELIVERED.

### 1.1 Payment — P0, money

**F1. Late payment on an auto-cancelled order is kept, not refunded.**
`UnpaidOrderTaskScheduler` cancels a CREATED order 30 minutes after
placement (`litemall_system.litemall_order_unpaid = 30`). The Stripe
PaymentIntent is created with only `metadata.orderId`; its id is NOT
stored on the order until payment succeeds, and nothing cancels it at
Stripe when the order is auto-cancelled. So:
- a customer who confirms the card after minute 30 is charged; the
  webhook then finds the order at 103, `markAsPaid()` throws, and
  `StripeWebhookController` answers 200 "received" (deliberately, to
  avoid retry storms). Card charged, order cancelled, no refund, ERROR
  log only. Stripe's retries re-run the same doomed path.
- **redirect and asynchronous methods make this systematic, not a
  race**: `StripeCardForm.tsx` uses `return_url: /orders`, so after a
  Klarna / iDEAL / Bancontact / 3-DS redirect the SPA never calls
  `/actions/pay`; only the webhook can mark the order paid. SEPA Direct
  Debit sits in `processing` for days — the SPA already shows "Your
  payment was not completed" for it, and the sweep cancels the order
  long before the debit settles. The user was told (Wave 24) to enable
  exactly these methods in the Stripe dashboard.
Anchors: `LitemallOrderOrchestratorService.handleStripeWebhook`,
`LitemallOrderServiceImpl.markOrderPaid` / `autoCancelOrder`,
`StripePaymentGatewayAdapter.createIntent` (no cancel method on the
port), `StripeWebhookController.orderPaid`.

**F2. A transient verification failure burns the Stripe event id.**
`handleStripeWebhook` claims `event.id` (INSERT IGNORE) before
`paymentGatewayPort.verify(...)`. If Stripe is briefly unreachable
during verify, the method returns normally with the claim committed; a
dashboard resend of the same event is discarded as a duplicate. The
order ages into 103 and F1 applies.

**F3. Stock is restored inside the cancel transaction with no rollback
compensation** (`restoreStockForOrder` → one Feign call per product,
failures swallowed). The placement path has the mirror-image guard
(`registerStockRestoreOnRollback`); the cancel path does not. Low
frequency; noted for completeness.

Dropped after verification: the agent-reported "mail failure rolls back
a captured payment" claim is false — `NotifyService.notifyMail` is
`@Async` (core `AsyncConfig`), and `notifySmsTemplateSync` returns null
without an SMS sender.

### 1.2 CJ placement and fulfilment — P1, orders go dark

**F4. Local status never reflects fulfilment.** After placement the row
stays 201 with `cj_order_status` as the only projection. Customer detail
shows "Processing" for a healthy order, a parked order, and an order
CJ has cancelled. The admin list cannot tell them apart either.

**F5. CJ order created but never paid from balance is silent forever.**
`CjLifecycleService.advance` → `payBalance` failure (insufficient CJ
balance is the obvious cause) is swallowed to `false` by `simpleCall`,
no timeline hop, no ops mail, re-tried every 5 min indefinitely. The
customer paid us; CJ never ships; nobody is told.

**F6. CJ CANCELLED after payment: no customer signal, no local status.**
One ops mail on the transition (only when `CJ_OPS_MAIL` is set — it is
blank in `.env.prod.example`), then the order leaves the sync predicate
forever. User decision 2026-07-20: no automatic money movement. That
decision stands, but the customer still sees "Processing" forever.

**F7. A parked order in manual mode is invisible and un-requeueable.**
`selectCjPlacementPending` requires `cj_placement_approved_time IS
NULL`, so an order approved and THEN rejected by CJ (`PLACEMENT_
REJECTED`) drops out of the pending list — the only admin surface. The
only recovery is a literal `UPDATE litemall_order SET cj_order_status =
NULL` printed into an ops email. No admin endpoint exists to requeue,
and the admin SPA has no "parked" state (renders
`approved-awaiting-placement` forever).

**F8. Retryable placement failures loop forever with no counter,
backoff, or escalation.** All HTTP-level CJ errors are classified
retryable (the Feign decoder erases the status), so bad credentials
retry every 5 minutes silently.

**F9. Open aftersale / refund request does not block approval or
placement** — only a `holdReason` string. An admin can approve a
refund-requested order and CJ will ship it.

**F10. Blank tracking number contradiction.** CJ `SHIPPED` with no
tracking number writes `ship_sn = ""`; the order reads "Shipped" while
`OrderTrackingService` (`hasText(shipSn)`) tells the customer "Not
shipped yet". Repaired only if CJ later supplies a number.

**F11. Sync predicate never retires 401/402/202.** Locally confirmed
orders are polled at 1.1 s each forever (burning the 20-slot batch);
202 orders no-op forever because `shipLocallyIfPaid` needs exactly 201.

**F12. Timeline operator is hard-coded `"admin"` for every ship**,
including the CJ poller's.

**F13. Duplicate-placement guard is JVM-local** (`inFlight` set). Fine
at one instance; the compose file already assumes one instance for the
mail outbox. Documented, not fixed.

### 1.3 Delivery and closure — P2

**F14. `LitemallOrderDeliveredEvent` has zero consumers.** No mail on
401/402, so the customer is never told the order closed nor that the
review / return window has started. Auto-confirm runs 15 days after
`ship_time` on a code default (`auto-confirm-days:15`, absent from yml)
while `litemall_system.litemall_order_unconfirm = 7` is ignored — two
sources of truth, neither documented as authoritative.

**F15. Refund after CJ paid from balance performs no CJ-side action.**
`cancelAtCjIfDeletable` only deletes while CJ status ∈ {CREATED,
IN_CART, UNPAID}. Local refund is paid from the store's pocket; the CJ
dispute vertical exists but is never linked. By design (cj-dispute
doc); raised as a decision, not a defect.

**F16. 202 REFUND_REQUEST is a customer dead end**: no handle options,
no withdraw, hidden from the Refunds page and every tab except "All".

**F17. Reviews have no purchase link**: `POST /srv/comment/post`
(goods-management) accepts any authenticated user, never marks
`order_goods.comment`, so "Unrated" never clears. goods-management
scope; raised.

**F18. Dead / contradictory helpers**: `LitemallOrderStatusQuery.
canBeCanceled` (CREATED||PAID vs dispatcher's CREATED-only),
`getStatusesForShowType` (missing 402), `isFinalStatus` (missing
402/103), `queryUnPaid(minutes)` ignoring its argument,
`publishOrderCreationEvents` unreachable branch, stale offline-pay
catch in `LitemallAdminOrderController` (:281-286), stale comment in
`orderSlice.ts:131-137`.

### 1.4 Customer-facing (gateway-api scope — raised, not built here)

- `GET /srv/order/{id}/timeline` has no consumer in the SPA while
  `Help.tsx` and `faqData.ts` promise "a status timeline".
- No carrier tracking link anywhere; tracking number is plain text.
- TrackingPanel fetches once behind a 1 h negative cache.
- `PaymentStatus.tsx` is a 3-second "thanks" page; nothing reconciles a
  redirect return (`redirect_status`, `payment_intent`) with the order.
- Order-list action errors are swallowed (`OrderList.act`).

### 1.5 Never exercised

Dev DB 2026-09-05: 101 orders; CJ orders reached at most
`UNSHIPPED` (sandbox, orders 79/94); 6 sandbox orders ended `CANCELLED`
at CJ with local status still 201; the only SHIPPED/402 rows are local
(non-CJ) admin-shipped test orders. Prod (per CLAUDE.md): CJ armed
2026-08-09, no order ever approved/placed; `litemall_mail_outbox`
empty; last paid order 2026-08-02.

## 2. Scope and boundaries

In scope (this worktree): `litemall-order`, `litemall-core` (mail
templates, payment port), `litemall-db` (hand-edited entity + mapper
XML where a query changes). **No Flyway migration** unless §3.B needs
the retry counter (V65 would be claimed after checking
`flyway_schema_history`; prod applied through V64).
Out of scope, raised as contracts: gateway-api (timeline UI, redirect
return reconciliation, tracking link, status wording), gateway-admin
(parked state + requeue button), goods-management (review purchase
check).

Principles carried over from the repo: typed errors, never a fake
success, no automatic money movement toward CJ, errno envelope, money
plain decimals, on-sale enforcement untouched, no new anonymous paths.

## 3. Work packages (in order)

### A. Payment money-safety (F1, F2) — P0

A1. `PaymentGatewayPort` gains `cancelIntent(id)` and
`retrieveStatus(id)` (Stripe impl + no-op in `Disabled`).
A2. `createPaymentIntent` persists the intent id on the order at
creation (`payment_intent_id` column exists, UNIQUE; latest intent
wins; `markOrderPaid` still overwrites with the VERIFIED id).
A3. `autoCancelOrder` reconciles with Stripe before cancelling when an
intent id is present:
  - `succeeded` → mark paid through the normal path (order proceeds);
  - `processing` (SEPA in flight) → **defer** the task, do not cancel;
  - anything else → cancel the intent at Stripe (a late confirm can no
    longer capture), then cancel the order as today.
  Stripe unreachable → defer, never cancel blind.
A4. Residual case (charge lands after 103 anyway): webhook and
`/actions/pay` detect "verified payment on a non-payable order" and
issue an automatic Stripe refund (`refund-order-<id>` idempotency key
already exists), write a `payment_refunded_late` timeline hop, enqueue a
customer mail "we received a payment for an expired order and refunded
it". Never silently 200.
A5. Claim the Stripe event id AFTER verification, or release the claim
on a transient verify failure (F2).
A6. Tests: sweep vs succeeded / processing / requires_action intents;
webhook on 103 → refund + hop; event id not burned on transient
failure; disabled gateway unchanged.

### B. Fulfilment visibility and control (F4–F12) — P1

B1. `payBalance` / `confirmOrder` failures write a `cj_sync` hop and,
on the FIRST failure and every 24 h after, an ops mail naming the
cause (insufficient balance is the expected one). No park (CJ retries
are cheap and correct); visible instead of silent.
B2. CJ `CANCELLED`: keep no-money-movement; additionally enqueue a
customer mail ("we could not fulfil your order; support will contact
you / refund") — **user decision D2** — and write a distinct
`cj_order_status` that the pending/attention list surfaces.
B3. Pending list includes parked rows regardless of approval stamp
(fix the query to match its own comment) and exposes `holdReason`
"CJ rejected: <msg>". New `POST /srv/private/admin/order/{id}/
cj-placement/requeue` (CAS: clears the sentinel only from
`PLACEMENT_REJECTED`; timeline hop; typed 422 otherwise). Contract
handed to gateway-admin.
B4. Retry accounting for retryable placement failures: attempt count +
last error kept in-memory per order is not durable; use the existing
timeline (count `cj_placement` "deferred" hops) — no schema change —
and escalate with an ops mail after N (config, default 12 = 1 h) and
stop retrying after M (default 288 = 24 h) by parking with a
`PLACEMENT_STALLED` sentinel that the requeue endpoint also clears.
B5. Approval refuses (typed 422 `[REFUND_PENDING]`) when order status
is 202 or an aftersale is open; the sweep re-checks the same predicate
(F9).
B6. `shipLocallyIfPaid` with a blank tracking number: still ship (CJ
did ship) but leave `ship_sn` NULL, not "", and make
`OrderTrackingService` answer `shipped:true, status:"TRACKING_PENDING"`
for 301 with no number (F10).
B7. Sync predicate excludes 401/402; 202 orders still sync (so a CJ
ship during a refund review is recorded) but the hop says why nothing
advanced (F11).
B8. Timeline operator "system" when the poller ships (F12).
B9. Tests for each; `LogbackConfigTest`-style pins where a query is
hand-maintained.

### C. Closure (F14, F16, F18) — P2

C1. `delivered` mail (core `MailTemplates` + `MailHtmlTemplates`, same
shell/tokens as the 2026-08-26 design): "delivered / auto-confirmed on
<date>; review or report a problem within <N> days". Listener on
`LitemallOrderDeliveredEvent`.
C2. One source of truth for auto-confirm days: read
`litemall_system.litemall_order_unconfirm` through `SystemConfig`
(as the unpaid window already does) with the yml value as fallback;
document which wins.
C3. Customer `POST /srv/order/{id}/actions/refund/withdraw` (202 → 201
CAS, only while no refund settled) — **user decision D4**; otherwise
F16 stays a raise to gateway-api for visibility only.
C4. Delete or correct the dead helpers in F18 (small, test-pinned).

### D. Raises (written into CLAUDE.md worktree blocks, not built here)

- gateway-api: timeline panel (endpoint exists); "Awaiting fulfilment
  review" / "Preparing at supplier" / "Fulfilment problem — support
  will contact you" status wording driven by `cj_order_status`;
  carrier tracking link (17track fallback); redirect-return page
  reconciling `payment_intent` with `/srv/order/{id}` polling; surface
  action errors.
- gateway-admin: parked/stalled state + Requeue button; attention list.
- goods-management: review purchase verification + mark
  `order_goods.comment`.

## 4. Acceptance (dev, through :9000 / :8090 / :18080)

- A: with Stripe test keys, an order left unpaid 30 min has its intent
  cancelled at Stripe (dashboard shows `canceled`); a `processing`
  intent (SEPA test method) keeps the order CREATED past 30 min; a
  succeeded-late intent marks the order paid, not cancelled; a forced
  post-103 payment is refunded automatically with hop + mail; resent
  webhook after a simulated verify outage is processed, not ignored.
- B: dummy CJ creds make placement observable (Wave-23 trick): parked
  order appears in pending with reason; requeue via the new endpoint
  clears it; approval of a 202 order is refused with the typed message;
  stalled placement escalates by mail and stops retrying at M; blank
  tracking renders TRACKING_PENDING; 402 orders drop from the sync set.
- C: delivered mail lands in MailHog for a customer confirm and for an
  auto-confirm; auto-confirm honours the system config value.
- Module tests green with real "Tests run:" counts (baseline 298 / 0).
- Prod (main session, after deploy, USER-SIDE): one real small EUR
  order end to end — pay, approve, watch CJ pay-from-balance, ship,
  tracking, delivered mail. This is the first real exercise of the
  whole pipeline and is the actual acceptance of this plan.

## 5. Order of delivery

A (one PR, deploy first — it is the money bug) → B → C. Each merges
separately; D is documentation only. Estimated new tests ≈ 40.

## 6. Decisions needed before code

- **D1 (A3/A4):** late payment on a cancelled order → automatic refund
  (recommended) or revive the order (re-deduct stock, re-queue CJ)?
- **D2 (B2):** when CJ cancels a paid order, may the store email the
  customer automatically (no money moves) — recommended yes.
- **D3 (B4):** stop retrying stalled placements after 24 h and park
  them (recommended), or retry forever as today?
- **D4 (C3):** give customers a "withdraw refund request" action?
- **D5 (F15):** should a local refund after CJ has been paid open a CJ
  dispute automatically? Recommended: not in this plan; separate
  decision.
- **D6:** confirm `CJ_OPS_MAIL` for prod (blank today) — without it B1/
  B2/B4 escalations are log-only.
