# Handoff: affiliate portal API (Wave 5 → gateway-admin)

Order serves two new surfaces; both are ALREADY protected by svcsecurity's
deny-by-default (they require the gateway machine token — no public-paths
change was made):

1. `/srv/private/affiliate/**` — affiliate self-service (gate edge-side to
   `ROLE_AFFILIATE`; ADMIN deliberately NOT allowed).
2. `/srv/private/admin/extract/**` — admin withdrawal console (rides the
   existing admin trust model, `ROLE_ADMIN`).

## What the edge must forward

Same relay as the existing `/srv/private/admin/**` prefix:

- the **machine token** (svc-to-svc `Authorization: Bearer …`), and
- **`X-User-Id`** = the uid claim of the validated affiliate JWT
  (`litemall_user.id`, NOT an admin id).

`X-User-Id` is the ONLY identity input: no affiliate endpoint takes a user-id
path/query/body parameter, so the portal is self-scoped by construction (no
IDOR surface). Route BOTH prefixes to `lb://order-service-app` BEFORE the
`/srv/**` catch-all, in BOTH `routes:` blocks.

Defense in depth: every affiliate endpoint re-checks the caller is a live
promoter (`is_promoter = 1`, not deleted) and answers HTTP 403 + errno 660
otherwise — a customer token smuggled onto the prefix gets nothing even if the
edge misroutes.

## Envelope

Success: HTTP 200 `{ "errno": 0, "data": <payload> }` (litemall-core
`ResponseUtil`). Failure: non-zero errno (table below) with
`{ "errno": N, "errmsg": "…" }`; business rejections use HTTP 422, the
promoter gate uses HTTP 403, malformed paging HTTP 400. Paged payloads are
`{ list, total, page, limit, pages }`.

## Errno table (660-family, minted in `interfaces/util/AffiliateErrno.java`)

| errno | HTTP | meaning |
|-------|------|---------|
| 660 | 403 | caller is not a live promoter (`is_promoter=1`, not deleted) |
| 661 | 422 | brokerage withdrawal below `litemall_brokerage_min_extract` |
| 662 | 422 | brokerage withdrawal exceeds available `brokerage_price` |
| 663 | 422 | extract approve/reject on a row that is not PENDING |
| 664 | 400 | malformed request (paging out of range, etc.) |

## Affiliate endpoints (`/srv/private/affiliate`)

### `GET /dashboard`
```json
{ "errno": 0, "data": {
    "available": 12.34,        // litemall_user.brokerage_price (withdrawable now)
    "frozenSum": 5.00,         // ledger sum: status=0, pm=1
    "lifetimeEarned": 40.00,   // ledger sum: status=1, pm=1
    "thisMonth": 7.50,         // ledger sum: pm=1, status>=0, add_time >= 1st of month
    "spreadCount": 3,          // live COUNT of users with spread_uid = me
    "referredOrders": 9        // ledger COUNT: pm=1, link_type='order', status>=0
} }
```

### `GET /records?page=1&limit=10`
Paged commission ledger, newest first. Row:
```json
{ "id": 1, "title": "Order commission", "price": 2.50, "pm": 1,
  "status": 0, "statusText": "frozen|valid|invalid",
  "linkType": "order|extract", "linkId": "<orderSn or extractId>",
  "mark": "5% of goods total 50.00 for order …", 
  "addTime": "…", "freezeTime": "…", "unfreezeTime": "…" }
```
Chips: status 0 = frozen, 1 = valid, -1 = invalid; `pm` 1 = income,
0 = withdrawal debit.

### `GET /team?page=1&limit=10`
Paged referred users, newest binding first. Row (PII-masked — this is all a
promoter may see):
```json
{ "nickname": "u***", "addTime": "…", "payCount": 0 }
```
Note: `pay_count` is surfaced as stored; nothing increments it in v1 (see the
lifecycle ADR).

### `GET /links`
```json
{ "errno": 0, "data": {
    "inviteCode": "A7",
    "registerUrl": "http://localhost:9000/register?invite=A7",
    "productUrlTemplate": "http://localhost:9000/product/{goodsId}?invite=A7"
} }
```
Base origin configurable via `litemall.affiliate.storefront-base-url`
(default `http://localhost:9000`). The SPA substitutes `{goodsId}` and renders
QR/copy client-side.

### `POST /extract`
Body: `{ "realName", "extractType": "bank|alipay|wechat", "bankCode",
"bankAddress", "extractAmount": 10.00 }`. Source is FORCED to brokerage.
Success data: `{ "id", "extractAmount", "balanceAfter", "status": 0 }` —
the amount is debited immediately, the request is PENDING for admin review.
Failures: 661 (below min), 662 (insufficient).

### `GET /extract/history`
Bare array (inside `data`) of the caller's brokerage-sourced withdrawals,
newest first: `{ id, realName, extractType, bankCode, bankAddress,
extractPrice, balance, status, failMsg, failTime, addTime }`.
Status: -1 rejected, 0 pending, 1 processing, 2 completed.

## Admin endpoints (`/srv/private/admin/extract`)

### `GET /list?status=&page=1&limit=10`
Paged withdrawal queue (ALL sources), newest first; optional status filter.
Row = extract/history row plus `userId` and `"source": "wallet"|"brokerage"`.

### `POST /{id}/approve`
Guarded PENDING(0) → COMPLETED(2). Payout itself is out of band (no PSP
integration); money was already debited at request time. Not pending → 663.

### `POST /{id}/reject`  — body `{ "reason": "…" }`
Guarded PENDING(0) → REJECTED(-1) + `fail_msg`/`fail_time`, and the amount is
refunded to the balance it came from: brokerage extracts get
`creditBrokerage` + their pm=0 ledger row flipped to invalid; wallet extracts
get the standard wallet credit (with bill entry). Not pending → 663.

## Config form keys (Sys/ConfigBrokerage)

`litemall_system` rows (seeded by V39, so `updateConfig` can round-trip them):
`litemall_brokerage_enabled` (`"true"|"false"`), `litemall_brokerage_rate`
(percent, e.g. `"5"`), `litemall_brokerage_freeze_days` (`"7"`),
`litemall_brokerage_min_extract` (`"10"`). Order reads them PER EVENT — a rate
change affects the NEXT commission, no restart needed.
