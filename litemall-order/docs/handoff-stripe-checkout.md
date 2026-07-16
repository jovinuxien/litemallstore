# Handoff — Stripe checkout, cart contract, server totals (Wave 7)

**Audience:** `gateway-api` (edge + customer SPA).
**Owner:** `order`. **Status of this doc:** §1 and §2 are LANDED (branch `fix/order`,
commit `0ef4198d3`) — build against them now. §3–§5 are the COMMITTED contract for work
in flight; the shapes are fixed, so you can build against them before the endpoints exist.

`order` and `gateway-api` **merge together or not at all** (Wave-7 header): verified
payments behind an unauthenticated edge is still exploitable, and enforced auth without
real payments strands the funnel.

---

## 1. BREAKING — `/srv/cart/items` takes identity from `X-User-Id` (LANDED)

All six RESTful cart endpoints now read the buyer from the trusted gateway header
instead of a query param / request body:

| Endpoint | Before | Now |
|---|---|---|
| `GET /srv/cart/items` | `?userId=` (required) | `X-User-Id` header |
| `GET /srv/cart/items/{id}` | `?userId=` | `X-User-Id` |
| `POST /srv/cart/items` | `userId` in body | `X-User-Id` |
| `PUT /srv/cart/items/{id}` | `?userId=` | `X-User-Id` |
| `DELETE /srv/cart/items/{id}` | `?userId=` | `X-User-Id` |
| `DELETE /srv/cart/items` | `?userId=` | `X-User-Id` |

**This fixes `GET /srv/cart/items`, which 400d on every logged-in fetch** — `cartApi.list()`
sends no `userId` and the param was required. The cart page has been running on
sessionStorage alone, silently swallowing the error. It now returns the real server cart.

A request with no forwarded identity gets the standard **errno 501 unlogin** envelope
(`MissingIdentityAdvice`), not another user's cart. No SPA change is required for the
header itself — the gateway already forwards `X-User-Id` for `/srv/cart/**` (that is how
`/srv/cart/index` has always worked).

### `POST /srv/cart/items` body shrank to identifiers + quantity

```jsonc
// now
{ "goodsId": 1181, "productId": 4506, "number": 1 }
```

`price`, `goodsSn`, `goodsName`, `picUrl`, `specifications` and `userId` are **deleted**
from `AddCartItemRequest`. The line is resolved from goods-management (same path as
`POST /srv/cart/add`), so the client can no longer assert its own price, name or image.
Those display fields were previously copied verbatim into permanent `order_goods` rows.

**Compatibility: your current SPA keeps working — extra fields are ignored, not rejected.**
Verified live: posting `{"goodsId":1181000,"productId":2,"number":1,"price":0.01,
"goodsName":"HACKED NAME","picUrl":"http://evil/x.png"}` persists the line at the **catalog
price (1500.00)** with the real name and image, and returns 201.

That leniency is explicit (`@JsonIgnoreProperties(ignoreUnknown = true)` on the DTO), and
worth knowing why: litemall-core's `JacksonConfig` looks like it already sets
`failOnUnknownProperties(false)`, but the plain `@Bean ObjectMapper` in that same class
overrides the builder customizer, so **order actually rejects unknown fields by default**.
Without the annotation, deleting `price` would have turned your existing checkout mirror
into a hard 402. If you add a field to any order-service DTO, don't assume core's setting
protects you.

### Two new submit-time 422s

Both arrive in the normal `submitFailed` envelope your existing inline error affordance
already renders:

- **"The price of "X" changed to N — review your cart and place the order again."**
  A checked line's price no longer matches the catalog. The server will not silently
  recompute a total the customer never saw. Recovery = re-fetch the cart and resubmit;
  re-adding the line re-prices it.
- **"goods-management returned no price for product N — it cannot be ordered right now."**

In practice the price-changed 422 should be rare for the SPA, because the checkout mirror
deletes and re-creates the server cart immediately before submit. It is most likely at a
flash-deal boundary (the deal lifecycle swaps SKU prices on a ~60s tick).

---

## 2. SPA work `gateway-api` owns

These are yours, not `order`'s — they live in `litemall-gateway-api` and overlap your
Task B (`orderSlice.ts`).

1. **Stop sending `price` in the checkout mirror** (`app/shared/reducers/orderSlice.ts`,
   the `baseAxios.post(.../cart/items, {...})` block ~:157-168). Send only
   `{goodsId, productId, number}`. Also drop `userId` from that body. The server ignores
   them today, but they read as authoritative and will be re-trusted by the next reader.
2. **Delete `remoteAddToCartThunk`** (`app/shared/reducers/cartSlice.ts:45`). It has
   exactly one reference — its own definition. It is never dispatched.
3. **Stop computing money client-side** (`app/views/commonViews/cart/Checkout.tsx:160`,
   `:201`, `:248`, `:674-701`). Today the subtotal and grand total are `reduce()`d from
   cart-carried prices while freight comes from the server — so preview and charge can
   silently disagree. Use `GET /srv/cart/checkout` (§4) once it lands. **This becomes
   mandatory with tax** (§5): the client cannot compute a tax-inclusive total.
4. **`cartApi.checkout` is currently dead code** (`app/shared/api/cartApi.ts:44-45`, zero
   call sites). Its `CheckoutSummary` type is already the agreed shape — §4 implements it.

---

## 3. Stripe payment (CONTRACT — in flight)

Today `processPayment()` accepts **any non-blank string** as proof of payment, and
`orderSlice.ts:228` fabricates `pi_stub_${orderId}` while `Checkout.tsx:731` discloses the
placeholder to the customer. Both sides of that stub go away together.

### Flow

1. **Create the intent server-side** — `POST /srv/order/{orderId}/actions/payment-intent`
   ```jsonc
   // 200
   { "errno": 0, "data": { "clientSecret": "pi_..._secret_...", "amount": 4930,
                           "currency": "usd", "publishableKey": "pk_..." } }
   ```
   `amount` is in **minor units** (integer cents), derived from the order's `actualPrice`.
   The intent carries `metadata.orderId`. Never create the intent client-side — the amount
   must not originate from the browser.

2. **Confirm client-side** with Stripe Elements using `clientSecret`.

3. **Report the real id** to the existing pay action — unchanged shape:
   ```jsonc
   POST /srv/order/{orderId}/actions/pay
   { "paymentMethod": "CREDIT_CARD", "paymentIntentId": "pi_1AbC..." }
   ```
   The server now calls `PaymentIntent.retrieve` and asserts **all** of: `status ==
   "succeeded"`, `amount_received == actualPrice` (minor units, integer compare),
   `currency`, and `metadata.orderId == orderId`. Any mismatch → rejected, order stays
   CREATED, no CJ placement. A PaymentIntent already bound to another order → rejected
   (DB-enforced unique).

4. **The webhook is authoritative**, not step 3. `POST /srv/order/webhook/stripe/order-paid`
   is anonymous + signature-gated + idempotent on `event.id`. The client call is a latency
   optimisation; a customer who closes the tab still gets a paid order.

### Failure semantics you must render

- **Stripe disabled** (`litemall.order.stripe.enabled: false`, the default) →
  `POST .../actions/payment-intent` returns a **typed error, never a fake success and
  never a `pi_stub_`**. Card payment must present as cleanly unavailable; wallet checkout
  is unaffected. Verify your UI in this state — it is the default in dev.
- Pay-action failures map to **HTTP 402** (`LitemallHttpResponseUtil:112-113`), not 422.
  That is pre-existing behaviour for the PAY operation type.

### Publishable key

Serve it through your Wave-6 `GET /auth/site-config` seam (`SiteConfigController.java:41`)
— same pattern as the Matomo tracker config. **Absent ⇒ card payment cleanly unavailable.**
The publishable key is not a secret, but it is environment-specific: read it from your own
config, do not hardcode it, and do not commit a fallback.

Note `litemall-core/src/main/resources/application-core.yml:18-22` currently holds
committed `pk_test_`/`sk_test_` keys under `litemall.stripe.*`. That block is legacy: its
`webhook-url` binds to no field and nothing reads it. `order`'s new config lives under
**`litemall.order.stripe.*`** with env-only secrets. Rotating/removing the core block is
flagged to `platform`, not to you.

---

## 4. `GET /srv/cart/checkout` — server-authoritative totals (CONTRACT — in flight)

Implements the `CheckoutSummary` shape already declared in `cartApi.ts:25-35`.

```
GET /srv/cart/checkout?addressId=&couponId=&cartId=      X-User-Id required
```

```jsonc
{ "errno": 0, "data": {
    "goodsTotalPrice": 49.30,   // sum of catalog-priced checked lines
    "freightPrice": 0.00,
    "taxPrice": 0.00,           // NEW vs the current type — see §5
    "couponPrice": 0.00,
    "orderTotalPrice": 49.30,
    "actualPrice": 49.30,       // the number to charge
    "availableCouponLength": 0,
    "checkedGoodsList": [ /* ... */ ] } }
```

It calls the **same three services submit calls** — `FreightCalculationService.quote`,
`promotionFacade.findUsableCoupon`, `priceCalculation` — so preview and charge provably
agree. That is the whole point: today they can't.

Add `taxPrice` to your `CheckoutSummary` type. It is `0.00` while tax is disabled (the
default), so you can wire it now.

---

## 5. Tax (CONTRACT — in flight)

US sales tax + EU VAT via Stripe Tax, `litemall.order.tax.enabled: false` by default.

**Tax fails CLOSED** — the one deliberate exception to the Wave-6 fail-soft habit. When
tax is enabled but the provider is unreachable, **checkout is blocked** rather than
shipping an untaxed order. Surface that as a retryable error (503), not a silent zero.
With tax disabled (dev default) `taxPrice` is `0.00` and nothing changes.

---

## 6. Config keys (`order` side, FYI)

| Key | Default | Notes |
|---|---|---|
| `litemall.order.stripe.enabled` | `false` | disabled ⇒ typed error, never a fake success |
| `litemall.order.stripe.secret-key` | *(none)* | ENV only, no committed fallback |
| `litemall.order.stripe.webhook-secret` | *(none)* | ENV only; webhook 400s without it |
| `litemall.order.stripe.currency` | `usd` | asserted against the PaymentIntent |
| `litemall.order.tax.provider` | `none` | `none` \| `stripe` |
| `litemall.order.tax.enabled` | `false` | enabled + unreachable ⇒ checkout blocked |

---

## 7. Routing

`/srv/cart/**` and `/srv/order/**` already route to the order service. **One new path
needs edge treatment:**

`POST /srv/order/webhook/stripe/order-paid` must be reachable by **Stripe** — no customer
JWT, no machine token (Stripe cannot present one). It is anonymous in the order service's
`svcsecurity` `public-paths` and gated by `Stripe-Signature` verification instead. When
you enumerate public paths in Task A, **this must be on the list**, alongside catalog,
search, product detail, `/auth/**` and health.

It receives no forwarded identity by design; the handler resolves the order from the
Stripe payload.
