# Edge notes — Stripe checkout (Wave-7 Task B)

> **RECONCILED — `order`'s contract won.** This started as the edge's day-one
> proposal (the plan requires the PaymentIntent shape agreed up front, and `order`
> had not started yet). `order` has since committed
> **`litemall-order/docs/handoff-stripe-checkout.md`, which is the authority** — the
> edge is built against that, not against what this file originally proposed.
>
> Three divergences were found and fixed edge-side; they are recorded in §0 because
> each would have broken the merge silently.
>
> What remains here: what the EDGE implements and the one thing `order` still owes
> an answer on (§5).

Audience: `litemall-order`.

---

## 0. Where the edge's proposal was WRONG (reconciled 2026-07-17)

| Thing | Edge proposed | `order` shipped (authoritative) |
|---|---|---|
| Create-intent path | `POST /srv/order/{id}/actions/create-payment-intent` | **`POST /srv/order/{id}/actions/payment-intent`** |
| Response | bare `{clientSecret, paymentIntentId, amount, currency}` | **`{errno:0, data:{clientSecret, amount, currency, publishableKey}}`** |
| Pay failure status | 422 | **402** (pre-existing for the PAY operation type) |
| **Webhook path** | `/srv/stripe/webhook/**` (invented) | **`POST /srv/order/webhook/stripe/order-paid`** |

**The webhook one was the dangerous one.** The edge had routed and public-listed a
path that does not exist, while the real path sits under `/srv/order/**` — which
Task A makes authenticated. Every Stripe webhook would have 401'd, and the failure
mode is silent: orders sit unpaid while Stripe retries into a wall. It is now an
explicit carve-out in `PublicPaths.STRIPE_WEBHOOK_POST`, covered by
`EdgeAuthorizationPolicyTest` and verified live (webhook passes; `/srv/order/list`,
`/actions/pay`, `/actions/payment-intent` and `/srv/order/webhook/other` all still
401).

The invented `stripe-webhook` gateway route was deleted: `/srv/order/**` already
routes to the order service, so no new route was ever needed.

---

## 1. What the edge implements (LANDED on `fix/gateway-api`)

- **Publishable key via `GET /auth/site-config`** (`stripePublishableKey`), per your §3.
  Config key `litemall.stripe.publishable-key`, blank default, ENV override, no committed
  fallback. Absent ⇒ the card radio is disabled with an honest reason and the UI falls back
  to wallet. **Never stubbed.** The secret key stays yours.
  - Deliberately NOT `litemall.stripe.publishedKey` — litemall-core's
    `application-core.yml` ships committed `pk_test_`/`sk_test_` values under that legacy
    block. This edge does not load the core profile (`active: default,db`) so there is no
    precedence trap today, but reusing the name would have invited one and served a
    committed test key by default. (Your note about retiring that block → platform: agreed.)
- **Real Stripe Elements** (`shared/payment/StripeCardForm.tsx`), deferred-intent flow:
  validate card → place order → `POST .../actions/payment-intent` → confirm with the
  returned `clientSecret` → report the REAL id to `/actions/pay`. The intent is created per
  order, only once that order exists — a charge must never exist with no order behind it.
- **`pi_stub_` is gone**, from the source and from the built bundle. `payOrder` now
  *rejects* a CARD payment with no PaymentIntent id rather than inventing one. The
  customer-facing "a placeholder authorisation is used for now" copy is deleted.
- **Cart mirror sends `{goodsId, productId, number}` only** (your §1/§2.1); `userId` is
  gone from the body and the params, and the SPA no longer decodes the JWT to assert who it
  is. `remoteAddToCartThunk` deleted (§2.2).
- **Server-authoritative totals** (§2.3/§4): goods, freight, tax, coupon and the charged
  total all come from `GET /srv/cart/checkout`. The client computes no money. A 503 blocks
  checkout with a retryable message and disables Place Order — no client-side fallback sum.
  `availableCouponLength` dropped; the picker keeps using `/srv/coupon/selectlist` (§4.2).

### One thing worth knowing back

`@stripe/stripe-js`'s main entry **injects js.stripe.com as an import side effect** — merely
bundling it hit Stripe (and its `m.stripe.network` fingerprinting endpoints) on checkout
load, with no key configured, no card selected and no cookie consent. The edge imports
`@stripe/stripe-js/pure` instead, which defers everything to `loadStripe()`. Verified: an
unconfigured checkout now makes zero requests to stripe.com. If `gateway-admin` ever mounts
Elements (it has `StripePayment.tsx`), it has the same trap.

---

## 2. 🔴 The gap your §4 does not address: /srv/cart/checkout prices an EMPTY cart

`GET /srv/cart/checkout` prices the **server** cart. **The SPA only ever writes the server
cart inside submit's mirror** — add-to-cart is sessionStorage-only, and
`remoteAddToCartThunk` (the one path that would have written it) was the dead code you
asked us to delete. So on the Checkout page, before submit, the server cart is empty and
`/srv/cart/checkout` returns zeros. Verified against the code: the only writes are
`placeOrder`'s mirror and `PUT /srv/cart/items/{id}` (server lines only).

**Edge resolution (landed):** Checkout now **mirrors each group, then prices it**, reusing
the exact mirror submit runs — debounced on the existing cart signature. Preview and charge
therefore read the same server cart and agree by construction. Groups are priced local-then-CJ
in submit's order, and the coupon rides only the first, matching `handlePlaceOrder`.

**No action needed from you** — recorded because your §4 reads as though the endpoint can be
called standalone, and the next reader will assume it. The deeper fix (a signed-in
add-to-cart writing straight to the server cart, making it authoritative and the mirror
unnecessary) is deliberately NOT in this wave.

---

## 3. Webhook — answered, with one thing to double-check

Resolved against your §7: the path is `POST /srv/order/webhook/stripe/order-paid`, it
is on the edge's public list, and it needs no new route (it already matches the
`customer-order` route). Verified live — see §0.

**One residual, worth a look on your side.** Your §7 says the webhook expects "no
customer JWT, **no machine token**" and is anonymous in svcsecurity's `public-paths`.
But the edge relays a machine token on every `lb://` route, and the webhook is on the
public list, so it IS relay-eligible: what your service actually receives will carry a
valid machine token and no `X-User-Id`.

That should be harmless — an anonymous `public-paths` entry does not care that a token
is present. Flagging only because your §7 describes a request shape (no token) that
differs from what the edge will really send (machine token, no identity), and if your
handler or a filter ever asserts "no Authorization header" on that path, it will fail
in a way that is hard to see. The signature check is unaffected either way.

If you would rather the edge did NOT tokenise it, say so — `MachineTokenRelayFilter`
can exclude the path in one line.

## 4. Sequencing / dependencies

- Both sides are built. The edge cannot merge without `order` (merge-together rule).
- **Still needs real Stripe test keys to verify end-to-end** (a stated user
  prerequisite). Verified WITHOUT them, edge-side: unconfigured ⇒ card cleanly
  unavailable + zero requests to stripe.com; configured ⇒ card enabled and Elements
  mounts; no server total ⇒ checkout blocked, never a client-side fallback sum.
  The live card payment (Elements → payment-intent → confirm → verified pay) is the
  one thing outstanding, and it needs your service booted with test keys alongside
  this branch.

## 5. Two things `order` should NOT do (scope)

1. **The plan's Task E0.3 assigns SPA edits to `order`** ("stop sending `price` in
   the mirror; delete the never-dispatched `remoteAddToCartThunk`"). Those files are
   `litemall-gateway-api/src/main/webapp` — **edge scope**. ✅ Agreed and DONE
   edge-side (your §2 says the same). Nothing for you here.

2. **🔴 Your Task E2 fix arms a latent SPA bug — FIXED edge-side, must land together.**
   `cartSlice.ts:130-145` merges the server cart into the local cart **additively**
   (`mergedCart[idx].number = local + server`, matched on `goodsId` alone). It is
   dormant **only** because `GET /srv/cart/items` 400s today (the client sends no
   `userId`), so the server cart is always empty. The moment you fix that 400, every
   Cart/Checkout mount starts summing quantities — silent inflation, and two SKUs of
   one product collapse into one line.
   ✅ Fixed on this branch: the server line now WINS instead of being summed, and lines
   match on `goodsId + productId` (a cart line is a SKU, not a product — matching on
   `goodsId` alone silently collapsed two variants into one). Flagging so you don't
   chase a phantom "quantities are wrong" bug from the server side, and so this branch
   is understood as a hard co-requisite of your E2.
