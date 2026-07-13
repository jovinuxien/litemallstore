# Handoff → gateway-admin: order export / offline pay / channel stat / batch aftersale (Wave 4)

All under existing routes (`/srv/private/admin/order/**`, `/srv/private/admin/aftersale/**`
→ order service; machine token + ROLE_ADMIN at the edge; relay `X-User-Id` — offline pay
stamps it into the timeline).

## 1 · CSV export — `GET /srv/private/admin/order/export`

Query params (all optional): `userId`, `orderSn`, `orderStatusArray` (repeatable,
e.g. `orderStatusArray=101&orderStatusArray=201`), `start`, `end` (ISO date or
datetime; end EXCLUSIVE — same filters as `/list`, which also gained start/end).

Response: `text/csv; charset=UTF-8`, `Content-Disposition: attachment;
filename="orders-<ts>.csv"`. File starts with the UTF-8 BOM (EF BB BF), rows are
RFC-4180 (CRLF, quote-doubling). Header:

```
id,orderSn,addTime,orderStatus,aftersaleStatus,source,deliveryType,consignee,mobile,address,countryCode,goodsPrice,freightPrice,couponPrice,orderPrice,actualPrice,payId,payTime,shipChannel,shipSn,userId
```

Row cap `litemall.order.export.max-rows` (default 10000): an over-cap export ends
with the literal trailer line `# TRUNCATED — row cap (N) reached; narrow the filters`.
SPA: plain `<a href>` / window.open — the response streams, no JSON envelope.

## 2 · Offline mark-paid — `POST /srv/private/admin/order/{orderId}/pay`

Body (optional): `{"reference": "SEB-2026-07-13-001"}` → `pay_id =
"OFFLINE:SEB-2026-07-13-001"` (no reference → `OFFLINE:admin:<ts>`).

- 200 `{errno:0, data:{id, orderSn, payId, success:true}}` — order is PAID, timeline
  gains `pay` + `admin_offline_pay` hops.
- 422 — order not in CREATED (message names the current status), or lost the race.
- **CJ orders: marking paid LIVE-FIRES the CJ createOrderV2 replay** (may move real
  money when `sandbox=false`). The confirm dialog MUST say so for `source="cj"` rows
  (the list/detail now carry `source`). See `adr-offline-mark-paid.md`.

## 3 · Channel stat — `GET /srv/private/admin/order/stat/channel?start=&end=`

```json
{ "bySource": [ {"source": "local", "orders": 41, "amount": 1234.50},
                 {"source": "cj",    "orders": 12, "amount": 401.00} ],
  "byTender": [ {"tender": "WALLET",  "orders": 30, "amount": 900.00},
                 {"tender": "OFFLINE", "orders": 2,  "amount": 80.00},
                 {"tender": "UNPAID",  "orders": 21, "amount": 655.50} ] }
```

`tender` = `pay_id` prefix before `:` (`WALLET`, `CARD`, `OFFLINE`, ...); orders with
no `pay_id` group as `UNPAID`. Window filters on `add_time` (end exclusive); omit both
for all-time. Closes the recorded `/srv/order/admin/stat` follow-up (dashboard card).

## 4 · Batch aftersale — `POST /srv/private/admin/aftersale/batch-approve | batch-reject`

Body: `{"ids": [5, 6, 99], "reason": "..."}` (`reason` used by reject only).

Response 200 (always, when `ids` non-empty):

```json
{ "errno": 0, "data": { "succeeded": [5, 6],
    "failed": [ {"id": 99, "errmsg": "aftersale 99 not found"} ] } }
```

Each id runs in ITS OWN transaction — a bad id never rolls back siblings (approved
siblings' refunds stick). Empty/missing `ids` → 422.
