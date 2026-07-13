# Handoff → gateway-api: in-store pickup (Wave 4)

## Route (ONE new line)

`/srv/store/**` → `lb://ORDER-SERVICE-APP`. The path is ANONYMOUS on the order side
(svcsecurity public-path) — no machine token or JWT required for store browsing, but
relaying them is harmless. `/srv/order/**` already covers everything else below.

## Store directory (checkout picker + store page)

- `GET /srv/store/list` → `{errno, errmsg, data: {list: [store], total}}`
- `GET /srv/store/detail?id=` → one store; hidden/unknown → `errno 404`
- store: `{id, name, intro, phone, address, detailedAddress, logo, latitude,
  longitude, businessHours}` (strings; lat/lng nullable)

## Submit body (`POST /srv/order/submit`) — new OPTIONAL fields

```json
{
  "cartId": 0, "message": "...",
  "deliveryType": "pickup",          // omit or "express" = unchanged behavior
  "storeId": 3,                       // required for pickup
  "pickupName": "Anna Svensson",     // required for pickup
  "pickupMobile": "+46701234567",    // required for pickup
  "addressId": null                   // OPTIONAL for pickup (ignored); required rule
                                      // for express orders unchanged (default addr fallback)
}
```

Pickup orders: freight is 0 (skip the freight-quote for the pickup tab or show 0),
coupons/groupon work unchanged. CJ (dropship) items cannot be picked up.

## 422 errmsg strings (submit, HTTP 422, `{errno: 422, errmsg}` envelope)

- `In-store pickup is currently unavailable — choose delivery instead.` (kill-switch)
- `Dropshipped items cannot be picked up in store — choose delivery or remove them.`
- `The selected pickup store is not available.` (unknown / hidden store)
- `Pickup contact name and mobile are required.`

## DTO additions (NON_NULL — express orders' payloads unchanged)

- `GET /srv/order/list` rows: `deliveryType` (`"express"` | `"pickup"`) — relabel the
  My-Orders tabs for pickup (e.g. "to ship" → "ready for pickup" when status 201).
- `GET /srv/order/detail`: `deliveryType`, `storeId`, `verifyCode`, `verifyTime`.
  `verifyCode` appears ONLY after pay (null until then) and ONLY on this owner-scoped
  read — render it big (the customer shows it at the counter; store detail via
  `GET /srv/store/detail?id={storeId}`). `verifyTime` set = already collected.
  For pickup orders `address` is the marker string `"PICKUP: <store name>"`.
- Status text for a paid pickup order is still `PAID`; after write-off it becomes
  `DELIVERED` and the timeline (`/srv/order/{id}/timeline`) shows a `writeoff` hop.

## Not for this SPA

Write-off scanning is admin-only (`/srv/private/admin/order/writeoff` — see
`handoff-gateway-admin-store-writeoff.md`).
