# Handoff — gateway-admin's CJ tracking panel + balance readout vs litemall-order Task E

- **Raised by:** `gateway-admin` worktree, 2026-07-10 (Wave-3 task 5).
- **Owner:** `order` worktree (Wave-3 Tasks C/D/E).
- **Status:** the admin SPA side is BUILT and shipped; the order-side
  endpoints did not exist yet (verified 2026-07-10: no tracking/balance
  mapping in `litemall-order`, no tracking handoff spec under
  `litemall-order/docs/`). The panel is written against the ASSUMED contract
  below and degrades gracefully until the real one lands.

## What the admin SPA now calls

Both ride the existing `admin-order` gateway route
(`/srv/private/admin/order/**` → `lb://order-service-app`, machine-token
relay + `X-User-Id`/`X-User-Roles` forwarding included) — no gateway change
is needed when the endpoints ship.

1. `GET /srv/private/admin/order/{orderId}/tracking`
   (order detail page, `TrackingPanel` in `OrderDetail.tsx`, client
   `adminOrderCjApi.ts`)
2. `GET /srv/private/admin/order/cj/balance`
   (dashboard `StatTile`, same client)

## Assumed contract (reconcile me against the real spec)

Per CLAUDE.md order Task E: "returning carrier + tracking number + event
list; no tracking yet → clean 'not shipped' payload, not an error". Assumed,
in either the legacy `{errno,errmsg,data}` envelope or a bare DTO (the client
accepts both):

```jsonc
// tracking — shipped
{ "errno": 0, "data": {
    "shipped": true,
    "carrier": "CJPacket",           // alias read: logisticName
    "trackNumber": "CJ123456789",    // alias read: trackingNumber
    "cjOrderStatus": "SHIPPED",      // optional, alias: orderStatus
    "events": [                       // alias read: trackInfo / trackingEvents
      { "time": "2026-07-09 14:02",  // alias: date / eventTime
        "status": "In transit",      // alias: trackingStatus
        "description": "Departed facility",  // alias: content / detail
        "location": "Shenzhen" }      // alias: area
    ]
} }
// tracking — not shipped (2xx, NOT an error)
{ "errno": 0, "data": { "shipped": false, "events": [] } }
// balance
{ "errno": 0, "data": { "amount": 12.34, "currency": "USD" } }  // alias: balance
```

The client normalises the listed aliases, so minor naming drift is fine;
anything further needs a `transformResponse` touch-up in `adminOrderCjApi.ts`
(one file). Failure modes (404 while unshipped, 5xx, CJ unreachable) all
render as a muted "tracking not available" note / "—" tile — never a broken
page — so the order side can ship incrementally.

## Second ask — project the CJ markers onto the ADMIN order payloads

`LitemallAdminOrderController.detail` (`GET /srv/private/admin/order/detail`)
builds its `order` map via `toRow()` and today exposes NO CJ fields, although
the aggregate carries them (`source`, `cjOrderId`, `cjOrderNum`; customer
DTOs already expose them per `handoff-gateway-api-cj-pay-first.md` §5).
Please add to the admin detail (and ideally the list rows):

- `source` ('local' | 'cj') — the SPA already renders a "CJ dropship" badge
  on the order detail header when present (`IOrderDetail.order.source`)
- `cjOrderId`, `cjOrderNum` — shown in the badge
- `trackNumber` once Task E persists it

Until then the badge simply doesn't render; the tracking panel works off the
tracking endpoint alone.

## Third ask (Wave 23, 2026-08-08) — project the V59 approval stamp onto the admin detail

**STATUS: DONE same day** — order-side projection landed (`8098293f9`, master
`0dcd94892`); live-verified through :18080: detail carries
`cjPlacementApprovedTime`/`cjPlacementApprovedBy` for approved orders (null
when unapproved), and a fresh SPA load of an approved order renders the stamp
+ "Approved — awaiting CJ placement" with no approve button (4/4 headless
checks). Original ask kept below for context.

`POST /order/{id}/cj-placement/approve` returns the stamp
(`approvedBy`/`approvedTime`) and the SPA renders it from that response, but
`GET /srv/private/admin/order/detail` does NOT project
`cj_placement_approved_time`/`cj_placement_approved_by` (verified live
2026-08-08: DB row stamped, detail payload carries no such keys). Until the
detail map adds them, a RELOADED approved-but-unplaced order falls back to
"Pending CJ approval" + the approve button — harmless (re-approve returns
idempotent `ALREADY_APPROVED` with the stamp, and the UI then renders the
correct state), but the stamp should survive a reload. The SPA already reads
`order.cjPlacementApprovedTime`/`cjPlacementApprovedBy` tolerantly (ISO string
or LocalDateTime array) — projecting the two columns in the detail `toRow()`
lights it up with zero SPA changes.

## Acceptance probe (once order Task E is live)

```
POST :18080/auth/login (admin123)                                → ROLE_ADMIN JWT
GET  :18080/srv/private/admin/order/{shippedCjOrder}/tracking    → events list renders on /admin/mall/order/{id}
GET  :18080/srv/private/admin/order/{unshippedOrder}/tracking    → "Not shipped yet" (not an error)
GET  :18080/srv/private/admin/order/cj/balance                   → dashboard tile shows a real figure
```
