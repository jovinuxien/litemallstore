# Handoff: CJ tracking + balance for the admin SPA (gateway-admin task 5)

Wave 3, order Task E. Backend endpoints are live in litemall-order (port 8085); the
admin gateway needs to route `/srv/private/admin/order/**` → `lb://ORDER-SERVICE-APP`
(already the case for the admin order list/detail — the new paths sit under the same
prefix) with the machine-token relay firing as usual. The customer endpoint is listed
for completeness (gateway-api already routes `/srv/order/**`).

## Endpoints

| Surface  | Method + path                                             | Auth |
|----------|-----------------------------------------------------------|------|
| customer | `GET /srv/order/{orderId}/tracking`                       | gateway-injected `X-User-Id`; owner-scoped, foreign/absent → errno 404 |
| admin    | `GET /srv/private/admin/order/{orderId}/tracking`         | edge gateway gates ROLE_ADMIN + machine token; controller adds no auth |
| admin    | `GET /srv/private/admin/order/cj/balance`                 | same |

Envelopes: customer returns the order module's `{errno, errmsg, data}`; admin returns
litemall-core `ResponseUtil` (`{errno:0, errmsg:"成功", data}`; unknown orderId →
`badArgumentValue`).

## Tracking payload (`data`)

Shipped CJ order (live from CJ `logistic/trackInfo`, cached 1h server-side):

```json
{
  "shipped": true,
  "status": "In transit",
  "carrier": "CJPacket Ordinary",
  "trackNumber": "CJPKL1234567890YQ",
  "origin": "CN",
  "destination": "SE",
  "deliveryDay": "7-15",
  "lastMileCarrier": "PostNord",
  "lastTrackNumber": "SE123456789",
  "events": [
    { "time": "2026-07-09 14:32:00", "status": "In transit",
      "description": "In transit — last mile: PostNord (SE123456789)" }
  ]
}
```

Not shipped yet (NOT an error — render a "not shipped" state):

```json
{ "shipped": false, "status": "NOT_SHIPPED", "carrier": null, "trackNumber": null,
  "origin": null, "destination": null, "deliveryDay": null,
  "lastMileCarrier": null, "lastTrackNumber": null, "events": [] }
```

Degrades to be aware of (all errno 0, never a 5xx):
- **CJ unreachable / CJ has no data yet** for a shipped CJ order → `shipped:true`,
  `trackNumber` + `carrier` set, `status:null`, `events:[]`, no `note`. Render "tracking
  details temporarily unavailable", not an error.
- **Locally-fulfilled order** (`source='local'`) that an admin shipped by hand →
  ~~same shape: `shipped:true` with carrier/trackNumber but no live events (there is no
  live tracking source for arbitrary local carriers)~~ **superseded by Wave 4 (Task D):**
  local shipped orders CAN now return real live events when an express provider is
  enabled (`litemall.order.express.provider: kdniao|onepass`, Caffeine-cached ~30min) —
  same `shipped:true` + `status` + `carrier` + `events[]` shape as the CJ example above
  (the local `events[]` may hold a full per-hop list, and origin/destination/deliveryDay/
  lastMile* stay null). When the provider is disabled or has no data, the payload keeps
  the old carrier+trackNumber shape and adds a `note` (below).

## `note` field (Wave 4, LOCAL orders only)

Nullable `note` on the tracking payload, present ONLY on locally-fulfilled shipped orders
(CJ payloads NEVER carry it; when null it is omitted from the JSON entirely):

| `note` | Meaning |
|---|---|
| `"tracking provider disabled"` | no express provider configured (`express.provider: none`) — carrier/trackNumber are still valid, there will never be live events until one is enabled |
| `"tracking temporarily unavailable"` | a provider IS enabled but returned nothing (no data yet, or provider error) — worth re-rendering later |

SPA notes:
- Treat **missing `note` + empty `events` on a shipped LOCAL order** as loading/pending
  provider data (a later render may fill events), not as a terminal "no tracking" state.
- Render the `note` text as a muted hint under the carrier/tracking number, never as an
  error banner.

CJ reports summary-level tracking only (no per-hop array), so `events` currently holds
ONE synthesized entry (current status + latest-event time). The array shape is
deliberate — render it as a list so per-hop detail can land without an SPA change.

Field nullability: everything except `shipped` and `events` may be null. All null fields
are serialized as `null` EXCEPT `note`, which is omitted when null (field-level NON_NULL —
keeps every pre-Wave-4 payload byte-identical).

## Balance payload (`data`)

```json
{ "amount": 12.34, "noWithdrawalAmount": 0.00, "freezeAmount": 0.00 }
```

USD figures from CJ `shopping/pay/getBalance`. CJ unreachable → `errno: 502`,
`errmsg: "CJ balance unavailable"` — show the card in an unavailable state.

## Related order-detail fields (already on `/srv/private/admin/order/detail`)

For the CJ panel on the admin order detail, `data.order` now also carries (V27/V33):
`source` (`'local' | 'cj'`), `cjOrderId`, `cjOrderNum`, and `cjOrderStatus` (last
CJ-side status: CREATED / IN_CART / UNPAID / UNSHIPPED / SHIPPED / DELIVERED /
CANCELLED — null for local orders and pre-Wave-3 CJ orders). The order timeline
(customer `GET /srv/order/{orderId}/timeline`) records every CJ status hop as a
changeType `cj_sync` entry.
