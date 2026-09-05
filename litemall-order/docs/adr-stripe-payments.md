# ADR — Stripe payments, tax, and the money path (Wave 7)

**Status:** implemented on `fix/order`.
**Scope:** `litemall-order` + V43. Companion: `handoff-stripe-checkout.md` (contracts).

---

## 1. The audit's headline P0 did not reproduce

The Wave-7 plan opens with: *"The client sets the price of the order… `POST /srv/cart/items
{"price":0.01}` + submit = a real 1-cent order."* **It does not, and it did not.**

`LitemallOrderDomainService.validateProductStock():49-55` already re-stamped the
authoritative catalog price onto every checked cart line, and
`LitemallOrderServiceImpl` calls it at :258 — eleven lines before `priceCalculation()` at
:269. There is exactly one call site of each, the loop is unconditional over the same
`cartList`, and `LitemallGoodsProductId.equals()` is implemented, so the variant lookup
matches. A tampered cart price was overwritten before it could reach the order total. The
comment on those lines says so explicitly.

**Why the audit reached the wrong conclusion:** it traced `cart-write → order-total`
through the data (the tampered row *is* persisted, and *is* what the cart page displays)
and stopped at `priceCalculation` reading `checkedGoods.getPrice()`. The re-stamp lives in
a method named `validateProductStock` — a stock check that quietly carries the money
path's most important guarantee. The name hid it.

**What WAS real, and is what Task E0 actually fixed:**

| Finding | Status before | Now |
|---|---|---|
| Cart IDOR — all six `/items` endpoints took `userId` from a param/body | **Live, unmitigated** | Header identity; anonymous → errno 501 |
| `goodsSn`/`goodsName`/`picUrl` copied verbatim into permanent `order_goods` rows | **Live, unmitigated** | Resolved from goods-management |
| Null catalog price fell through to the cart-carried value | **Live** | 422, fails closed |
| `GET /srv/cart/items` 400d on every logged-in fetch | **Live** | Returns the server cart |
| Cart display vs charged total could diverge silently | **Live** | 422 "prices changed" |
| `{"price":0.01}` → 1-cent order | **Never worked** | Still doesn't; now also can't be asserted |

**Consequence for sequencing:** E0 was *not* a Stripe blocker. The plan's rationale —
"amount-matching compares against an already-poisoned order total" — was false; the total
was always server-derived. The two were done independently. Approved by the user
2026-07-16 before implementation.

**Lesson worth keeping:** `validateProductStock` is now documented as the money path's
guard, not a stock check, because the next person to "simplify" it will otherwise hand the
price back to the client.

---

## 2. Why the payment reference got its own column

The plan said: *"Add a UNIQUE index on the payment reference."* Impossible as written.
`pay_id` is an overloaded tender label holding `WALLET` (18 dev rows share that literal),
`CREDIT_CARD:pi_...`, and `OFFLINE:<ref>`. A UNIQUE index on it could never be created.

V43 adds `payment_intent_id varchar(63) NULL UNIQUE`. MySQL permits many NULLs under a
UNIQUE index, so wallet and offline tenders (which have no PSP reference) don't collide,
while every real PaymentIntent is unique **by storage, not by convention**.

**Why not an application-level pre-check:** any `SELECT`-then-`INSERT` can be raced by two
concurrent pays. The index decides at write time; `markOrderPaid` lets
`DuplicateKeyException` roll the transaction back and the REST layer answers 402. Verified:
binding one PaymentIntent to a second order returns `Duplicate entry 'pi_replay_test' for
key 'uk_order_payment_intent_id'`.

---

## 3. Verification asserts four things, and fails closed

`PaymentIntent.retrieve` then assert **all** of:

1. `status == "succeeded"`
2. `amount_received == order.actualPrice` in **minor units**
3. `currency` matches the configured currency
4. `metadata.orderId == order.id`

(4) is the non-obvious one: without it, a genuine PaymentIntent for a cheap order verifies
against an expensive one whenever the amounts happen to match. (2) uses
`movePointRight(2).longValueExact()` — `LitemallMoney` normalises to exactly scale 2 in its
constructor, so the shift is lossless and cannot silently truncate a cent. No floats.

**Everything fails closed.** A rejection — including "Stripe is disabled" and "Stripe is
unreachable" — leaves the order CREATED with no CJ placement. The only safe answer to *"did
this get paid?"* when we cannot tell is **no**. `DisabledPaymentGatewayAdapter.verify()`
rejects rather than accepts, so no configuration mistake can mint a paid order; that
position was previously occupied by code that accepted any non-blank string.

**`PaymentGatewayPort` deliberately breaks this module's never-throws port contract.**
`ExpressQueryPort` returns `Optional.empty()` for "miss or error, indistinguishable on
purpose" and `ReceiptPrinterPort` never throws, because a missing tracking number or an
unprinted receipt is survivable. Money is not: an error collapsing into "no result" reads
as "not paid" or "refunded but nothing moved". So verification returns an explicit
accept/reject **with a reason**, and operations that can only be reported honestly
(create-intent, webhook parsing) throw.

---

## 4. The webhook is authoritative; the client call is a latency optimisation

`POST /srv/order/webhook/stripe/order-paid`.

- **Anonymous, and it must be** — Stripe cannot present a machine token. It is the only new
  anonymous path this wave. Its `Stripe-Signature` **is** its authentication; nothing
  upstream authenticates it. Verified: forged signature → 400, absent signature → 400, and
  it reaches the signature check with no machine token (400, not 401).
- **Raw body** (`@RequestBody String`) — the signature covers the exact bytes Stripe signed,
  so re-serialising through a DTO would break it.
- **Idempotent on `event.id`** via `litemall_stripe_event`. `claim()` is `INSERT IGNORE`
  against a UNIQUE key: 0 rows means another delivery already handled it. A read-then-write
  check would let two concurrent deliveries both pass. Rows are never soft-deleted — a
  purged row would let an old event replay. Verified: a replayed delivery logs "already
  processed" and no second row appears.
- **Signature proves authenticity, not truth.** A validly-signed event still re-verifies the
  amount against the order before paying it. Verified: a correctly-signed
  `payment_intent.succeeded` for order 80 with a matching amount did **not** pay it, because
  retrieval failed — order stayed CREATED.
- **200 for everything except a bad signature.** A non-2xx makes Stripe retry, and retrying
  will not fix a bug on our side; it just floods us. Genuine failures are logged.

**Token/key renewal:** none needed — `sk_live_`/`whsec_` do not expire. Rotation is manual
(Stripe dashboard → new key → update the env var → restart). Keys are ENV-only with no
committed fallback.

---

## 5. Why order owns `litemall.order.stripe.*` and not core's `litemall.stripe.*`

litemall-core ships `litemall.stripe.*` in `application-core.yml` with **committed
`pk_test_`/`sk_test_` keys**, a `webhook-url` pointing at `example.com` that binds to no
field in `StripeProperties`, and a `StripeConfig` that sets the global static
`Stripe.apiKey` at boot. Nothing reads any of it.

order is Stripe's only consumer, so it owns its own namespace under the existing
`litemall.order.*` seam convention. The adapters pass the key via **per-call
`RequestOptions`** rather than the global static — otherwise which key we charge with would
depend on bean-init order across modules.

Retiring core's block and rotating those keys is **platform's** (Wave-7 Task B), flagged
rather than done here: core is shared, and editing it mid-wave means installing it and
restarting every dependent.

---

## 6. Refunds fail loudly

`settleRefundToTender` previously logged `"would reverse"` and returned the full amount, so
the order flipped to REFUNDED and `refund_amount` recorded money that never moved. The
customer's refund existed only in our database.

Now `Refund.create` with a per-order idempotency key. On failure a
`LitemallRefundFailedException` rolls the transaction back: the order stays REFUND_REQUEST,
the aftersale stays open, the admin sees the error, and it is retryable. **A refund that
silently didn't happen is worse than one that visibly failed.**

**OFFLINE tender:** `paidTender` cannot parse `OFFLINE:<ref>` back to an enum constant (by
design — it is an admin annotation that money arrived out of band, not a chargeable
tender), so those orders reach the card branch with no PaymentIntent. They are now
**refused with an actionable message** instead of silently recording a 0.00 refund while
flipping REFUNDED. That is a behaviour change for the 2 such dev rows, and the honest one.

---

## 7. Tax is the one thing that fails CLOSED

Every other dependency here degrades: goods-management down is a retryable 503, a coupon
outage is a 503, a printer outage is a log line. Tax does not, because its failure mode is
different in kind: an untaxed order **completes**, the customer is happy, and the liability
is discovered at filing time when the money is gone. Silent and permanent beats loud and
retryable, so tax blocks checkout.

- `ZeroTaxAdapter` is the **only** legitimate source of a zero. "We are not collecting tax"
  and "we could not work out the tax" must never look the same.
- Boot **fails** on `enabled=true` + `provider=none` — that combination reports tax as on
  while collecting nothing.
- Verified: enabled + failing provider + known destination → clean 503 (both preview and
  submit). No destination yet → 0.00, preview renders.

**Coupons are allocated across lines proportionally.** Stripe's Calculation API has no
discount concept, so a discount must arrive as reduced line amounts; passing full prices
would tax a larger sale than happened and **over-collect from the customer**. The rounding
remainder lands on the last line so the taxable base sums exactly to `goods − coupon`.

**IOSS stays consistent with VAT (`CjDropshipOrderFacadeImpl:53-62`).** `ioss-type: 0`
(the default) omits the field and lets CJ's dashboard setting decide. **If you enable EU
VAT collection you must also set `ioss-type` to declare it**, or the buyer pays VAT at
checkout *and* import VAT at customs. This is configuration, not code — recorded here
because nothing in the code will remind you.

---

## 8. Preview and charge agree by construction

`GET /srv/cart/checkout` exists now. `cartApi.ts` has called it since Wave 4; it never
existed, so the SPA reduced its own grand total from cart-carried prices while only freight
came from the server — two implementations of one money chain, free to disagree (most
visibly at a flash-deal boundary: see one price, get charged another).

`CheckoutSummaryService` **delegates**: same `FreightCalculationService`, same
`LitemallPromotionFacade`, same `TaxCalculationPort`, and it reuses
`LitemallOrderServiceImpl.buildTaxableOrder`. A second implementation is the bug; the only
safe preview is one that runs the same code.

Soft where it can be, hard where it must be: an unpriceable coupon shows as no discount
(submit 503s there, so the previewed total is never *lower* than the charge), but an
enabled tax provider that fails → 503.

---

## 9. Deferred, with reasons

**CJ placement stays inside the payment transaction** (plan Task E4 asked to move it).
Approved by the user 2026-07-16.

The plan's premise was slightly off — `CjFulfillmentService` has no `@Transactional` of its
own; it inherits the orchestrator's. But the substance is right: a synchronous Feign call
inside a money transaction holds the `markPaidIfCreated` row lock and a pooled connection
for a CJ round-trip.

It is not free to remove. The placement is **deliberately** in-transaction: its javadoc
states a CJ rejection must roll back the wallet debit and the PAID status together, and the
REST layer surfaces that as a clean payment failure. Moving it AFTER_COMMIT trades that
atomicity for a compensation problem — paid orders with no fulfilment, needing a retry path
and an admin surface for operators to see them. Done badly, that loses money silently,
which is worse than the lock it fixes. It is its own vertical.

**Also deferred, flagged to platform:**
- `FlywayMigrationTest` is JUnit 4 while litemall-db ships only the JUnit 5 provider, so
  **surefire silently runs 0 tests** — it has never run in CI. Hand-run via JUnitCore here
  (43/43 migrations, 4/4 green). Adding `junit-vintage-engine` would also switch on
  `DbTest`/`StockTest`/`StatMapperTest`, which need a live DB — a CI decision, not a
  drive-by.
- litemall-order's test tree does not compile at all (`PostgreSqlTestContainer` needs
  `org.testcontainers:postgresql`, never declared; CJ test constructor drift). Pre-existing.
- Rotating core's committed Stripe test keys.

**Known trap for the next reader:** litemall-core's `JacksonConfig` *looks* like it sets
`failOnUnknownProperties(false)`, but the plain `@Bean ObjectMapper` in the same class
overrides the builder customizer, so **order rejects unknown JSON fields by default**. This
turned "delete `price` from `AddCartItemRequest`" into a hard 402 for the live SPA until
`@JsonIgnoreProperties(ignoreUnknown = true)` was set on the DTO. Do not assume core's
setting protects you.

---

## 10. What still needs real credentials

Everything above is verified with Stripe **disabled** and with **dummy** keys. These need
real Stripe test keys and cannot be faked:

- A real Elements payment verifying end-to-end and paying an order once.
- A **tampered amount** rejected (needs a real intent whose `amount_received` disagrees).
- A **replayed PaymentIntent** rejected through the full pay path — the storage layer is
  proven (`Duplicate entry … uk_order_payment_intent_id`), but with dummy keys verification
  rejects before the insert, so the end-to-end path is untested.
- A refund visible in the Stripe dashboard.
- Real US/EU tax figures from Stripe Tax (needs nexus/OSS registration).

To verify once keys arrive:

```bash
LITEMALL_ORDER_STRIPE_ENABLED=true \
LITEMALL_ORDER_STRIPE_SECRET_KEY=sk_test_... \
LITEMALL_ORDER_STRIPE_WEBHOOK_SECRET=whsec_...   # stripe listen --forward-to
```

## 11. The unpaid sweep asks Stripe before it cancels (2026-09-05, lifecycle plan package A)

The lifecycle audit (`plan-order-lifecycle-e2e.md`, F1/F2) found a money hole that §4's
"webhook is authoritative" did not cover: the PaymentIntent was created with only
`metadata.orderId`, its id was not stored until payment succeeded, and nothing cancelled
it when the unpaid sweep cancelled the order 30 minutes after placement. A charge landing
after that hit `markAsPaid()` on a SYSTEM_CANCELED order, which threw into the webhook
controller's deliberate 200. Charged, cancelled, no refund. Redirect methods (the SPA's
`return_url` is `/orders`; nothing calls `/actions/pay` afterwards) and SEPA (`processing`
for days) made it systematic rather than a race.

Decisions:

- **The intent id is recorded at mint** (`recordPaymentIntentIfCreated`, CREATED-guarded).
  Latest wins; a re-mint first inspects the previous intent — succeeded ⇒ settle it and
  refuse a second charge (`noRollbackFor` keeps the settlement under the refusal);
  processing ⇒ refuse; pending ⇒ cancel it at Stripe — so only ONE live intent can ever
  capture for an order.
- **`UnpaidOrderReconciler` decides, the sweep only owns task rows.** SUCCEEDED ⇒ settle
  (same path as the webhook, `settleVerifiedPspPayment`, REQUIRES_NEW through the proxy);
  PROCESSING ⇒ defer 60 min; UNAVAILABLE ⇒ defer 5 min, never cancel blind; PENDING ⇒
  cancel at Stripe FIRST, then the order; Stripe's refusal ("already succeeded") wins.
  Knobs `litemall.order.unpaid-reconcile.*` with explicit env placeholders.
- **Stray charges are refunded, not logged.** A succeeded intent presented for an order
  that cannot take it (cancelled, or already paid by a different intent) is reversed under
  its own idempotency scope (`refund-order-<id>-<intent>` — the plain key stays reserved
  for the order's real refund), gets a `payment_refunded_stray` hop and a customer mail
  (`payment-refunded`). A refund Stripe refuses is committed as a `payment_stray_unrefunded`
  hop plus an ops mail — durable and loud, never a rolled-back trace.
- **Transient verification failures no longer burn the event id.** `PaymentVerification`
  carries `retryable`; the orchestrator throws
  `LitemallPaymentTemporarilyUnavailableException`, the transaction (and the claim) rolls
  back, and the controller answers **503** so Stripe redelivers. Our own bugs still get 200.
- **Client `/actions/pay` is idempotent** against a payment the webhook or the sweep
  already settled with the same intent, and routes a stray charge through the same refund.

§4's narrative ("a crash mid-processing leaves the event claimed … recoverable via
dashboard resend") is superseded for the outage case by the 503 above; it still holds for
a crash after verification.

