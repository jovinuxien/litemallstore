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
  `trackNumber` + `carrier` set, `status:null`, `events:[]`. Render "tracking details
  temporarily unavailable", not an error.
- **Locally-fulfilled order** (`source='local'`) that an admin shipped by hand → same
  shape: `shipped:true` with carrier/trackNumber but no live events (there is no live
  tracking source for arbitrary local carriers).

CJ reports summary-level tracking only (no per-hop array), so `events` currently holds
ONE synthesized entry (current status + latest-event time). The array shape is
deliberate — render it as a list so per-hop detail can land without an SPA change.

Field nullability: everything except `shipped` and `events` may be null.

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
