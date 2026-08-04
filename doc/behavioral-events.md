# Behavioral event log — Phase 0 contract (frozen vocabulary)

Status: ACTIVE contract (2026-08-04). goods-management owns the schema (V49)
and the ingest path; gateway-api and order code to THIS document, not to each
other's branches. Changing an event name or required field after data has
accumulated means a backfill or a permanent seam in the dataset — treat the
vocabulary below as frozen and extend it only when something downstream
actually needs a new type.

## Principles

- **Client-side for intent, server-side for value.** Anything that affects a
  revenue number or a future training label (`purchase`, `refund`) originates
  in the order service, never the SPA.
- **Events are immutable.** No updates, no soft delete (`litemall_user_event`
  deliberately has NO `deleted` column — it is an append-only log, not a
  mutable entity). Identity is resolved at query time via
  `litemall_visitor_identity`; `user_id` is never backfilled onto old rows.
- **Tracking can never hurt the store.** Ingest is fire-and-forget: bounded
  in-memory queue that drops (with a WARN) under burst, batch inserts off the
  request thread, every failure logged and swallowed.
- **Prior consent, strictly (user decision 2026-08-04).** No identity cookies
  and no events, for any region, until the visitor grants consent. Pre-consent
  pageviews are deliberately lost.
- **No raw IP, no raw user agent is ever stored.** `country_code` comes from
  Cloudflare's `CF-IPCountry` request header (absent in dev ⇒ NULL);
  `device_type` is derived at ingest from the User-Agent, which is then
  discarded.

## Identity (edge-owned)

The client NEVER generates or transmits visitor/session identity — the edge
(litemall-gateway-api) owns it, exactly like `X-User-Id`:

| Cookie | Set by | Value | Attributes |
|---|---|---|---|
| `lm_consent` | SPA JS on the consent choice | `granted` \| `denied` | `Max-Age=34128000` (13 months), `Path=/`, `SameSite=Lax`, `Secure` (prod), NOT HttpOnly (the SPA reads/writes it; mirrors the existing `localStorage.cookieConsent`) |
| `lm_vid` | edge WebFilter | UUID v4 | `Max-Age=34128000` (13 months), `Path=/`, `HttpOnly`, `SameSite=Lax`, `Secure` (prod) |
| `lm_sid` | edge WebFilter | UUID v4 | `Max-Age=1800`, rolling — re-sent on every request, so 30 min of inactivity ends the session | 

Edge filter contract (gateway-api half):

1. ALWAYS strip inbound `X-Visitor-Id` / `X-Session-Id` (spoof protection —
   same pattern as `IdentityForwardingFilter` strips `X-User-Id`).
2. If `lm_consent=granted`: mint `lm_vid`/`lm_sid` when absent or expired
   (Set-Cookie on the response), and forward `X-Visitor-Id`/`X-Session-Id`
   headers downstream on every `/srv/**` request — including the very request
   that carried the fresh grant, so the consent POST itself is attributable.
3. If `lm_consent` is absent or `denied`: forward nothing; when `denied` and
   identity cookies exist, expire them (`Max-Age=0`).
4. `PublicPaths` gains `TRACK_POST = /srv/track/**` (POST only) — the second
   sanctioned anonymous POST after the Stripe webhook (user-approved exception,
   2026-08-04). Mitigations: insert-only side effect, event-type whitelist,
   hard size caps, edge-owned identity, ingest drops batches without
   `X-Visitor-Id`.

Service-side consent proof: the ingest endpoint treats the presence of
`X-Visitor-Id` (which only the edge can inject, only under a granted consent)
as the proof of lawful basis and DROPS whole batches without it.

## Ingest endpoint (goods-management)

`POST /srv/track/collect` — routed by the existing `/srv/**` catch-all; public
POST at the edge; `/srv/track/**` added to goods-management
`litemall.svcsecurity.public-paths`.

```json
{ "events": [ {
    "eventId":   "uuid-v4 (client-generated; idempotency key)",
    "type":      "one of the client vocabulary below",
    "occurredAt": 1722790000000,
    "goodsId":   123,          "productId": 456,   "categoryId": 789,
    "searchQuery": "…",        "position": 3,      "pageType": "pdp",
    "payload":   { "free-form long tail": true }
} ] }
```

- `occurredAt` is **epoch millis** (never a date string — the module's
  ObjectMapper serializes/parses `LocalDateTime` as arrays; epoch millis
  sidesteps it). Clamped at ingest to `[received_at − 7d, received_at + 5min]`
  — client clocks are wrong; `received_at` is the audit anchor.
- Batch cap 50 events; excess dropped, counted in the response. Unknown JSON
  fields ignored (`@JsonIgnoreProperties`). Unknown/server-only types dropped
  silently. Response: `{errno:0, data:{accepted,dropped}}` — immediately after
  enqueue, before any DB write.
- `user_id` comes ONLY from the edge-verified `X-User-Id` (via `UserContext`),
  never from the body. `origin` is set by the channel: this endpoint always
  writes `origin=1` (client); the order listeners write `origin=2` (server).
- **Stitching:** any batch carrying both `X-User-Id` and `X-Visitor-Id`
  upserts (`INSERT IGNORE`) a `litemall_visitor_identity` row — login and
  Wave-16 guest-claim stitch automatically, with no auth-path changes.

`POST /srv/track/consent` — records the consent decision server-side (the
"prove lawful basis" row): `{ "choice": "granted"|"denied", "scope":
"analytics", "occurredAt": 1722790000000 }`. Recorded even when
`X-Visitor-Id` is absent (a denial has no identity by design).

The SPA emitter (gateway-api half): buffer events in memory, flush every 5 s
or at 20 buffered events via the normal axios path, and on
`visibilitychange`→hidden / `pagehide` flush via `navigator.sendBeacon`
(Blob, `application/json` — same-origin, no preflight). Everything wrapped in
try/catch, fail silent. Emit only while the consent store says granted; the
existing pending-buffer semantics of `matomo.ts` apply.

## Vocabulary — exactly ten types

| type | origin | required columns | emitter |
|---|---|---|---|
| `page_view` | client | `page_type` (path in payload) | route-change hook (alongside `MatomoTracker`) |
| `view_item` | client | `goods_id` | PDP (`Detail.tsx`, existing `trackProductView` call site) |
| `view_category` | client | `category_id` | category landing (`/category/:id`) |
| `search` | client | `search_query` | search submit (`Search.tsx`) |
| `click_result` | client | `goods_id` (`position`, `search_query` opt.) | result-card click (`ProductHit.tsx`) |
| `add_to_cart` | client | `goods_id` (qty/price in payload) | PDP (existing `trackAddToCart` call site) |
| `remove_from_cart` | client | `goods_id` | cart page |
| `begin_checkout` | client | payload cart summary | `Checkout.tsx` (existing call site) |
| `purchase` | **server** | `user_id`; payload `{orderId, orderSn, total, items:[{goodsId, productId, qty, price}]}` | order service, AFTER_COMMIT on `LitemallOrderPaidEvent` |
| `refund` | **server** | `user_id`; payload `{orderId, orderSn, amount}` | order service, AFTER_COMMIT on `LitemallOrderRefundedEvent` |

One `purchase` row per order (items ride in the payload). The client-side
Matomo/Pixel `trackPurchase` stays as-is (third-party marketing attribution);
the first-party `purchase` row comes exclusively from the order service — no
double counting. Server rows have NULL `visitor_id`/`session_id` (the Stripe
webhook has no browser cookie); they join through `user_id` +
`litemall_visitor_identity`.

## Order-service contract (order worktree)

Two listeners, exact shape of `CustomerMailEnqueueListener` (AFTER_COMMIT,
`fallbackExecution=false`, 1-thread daemon executor, bounded queue, rejection
= WARN, every submit try/caught so payment can never fail): on
`LitemallOrderPaidEvent` insert a `purchase` row, on
`LitemallOrderRefundedEvent` a `refund` row, via `LitemallUserEventMapper`
(`INSERT IGNORE`; `event_id` = deterministic UUID v3-style from
`"purchase:"+orderId` / `"refund:"+orderId+":"+amount` so replays and event
re-deliveries stay idempotent). `occurred_at = received_at = now()`,
`origin=2`, `country_code`/`device_type`/`locale` NULL.

## Schema (V49)

Three tables — see `V49__behavioral_event_log.sql`: `litemall_user_event`
(hybrid: indexed hot columns + JSON `payload` long tail; `UNIQUE(event_id)`
absorbs retries), `litemall_visitor_identity` (append-only stitching),
`litemall_consent_record` (lawful-basis audit). GDPR erasure hook (later
phase): delete by `user_id` plus that user's linked `visitor_id`s.
