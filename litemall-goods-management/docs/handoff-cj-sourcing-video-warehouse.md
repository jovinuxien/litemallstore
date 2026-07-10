# Handoff — CJ sourcing / product videos / warehouse info (Wave 3, 2026-07-10)

Producer: `litemall-goods-management` (this worktree). Consumers: `litemall-gateway-admin`
(sourcing + warehouse admin surfaces) and `litemall-gateway-api` (product-video surface on the
goods detail page). All three verticals EXTEND the existing CJ ACL stack (`CJRequestUtils` +
`CJTokenService` + `CJProductService`'s shared 1-QPS limiter + `cj:raw:*` Redis staging, 6h TTL).
Every new outbound CJ call runs on a timeout-configured RestTemplate (connect 5s / read 15s,
`CjTimedRestTemplates`) — CJ-unreachable degrades to a clean errmsg or empty payload, never a
hang or raw 500.

## Endpoints

| Consumer | Endpoint | Response `data` |
|---|---|---|
| admin SPA | `POST /srv/private/admin/cj/sourcing` body `{cjPid?, productName?, productImage?, productUrl?, price?, remark?}` | the persisted row (below), incl. `cjSourcingId` when CJ accepted |
| admin SPA | `GET /srv/private/admin/cj/sourcing?page=&limit=&refresh=&sourceId=` | list: `{list:[row], total, page, limit}`; with `sourceId`: one row (402 if unknown) |
| admin SPA | `GET /srv/private/admin/cj/warehouse?id=<storageId>` | `{id, name, areaId, areaCountryCode, address1, address2, contacts, phone, city, province, zipCode, isSelfPickup, logisticsBrandList:[{id,name}]}` |
| customer SPA | `GET /srv/goods/videos?id=<goodsId or cj_pid>` | `[{id, name, url, duration, width, height}]` |

Sourcing row shape (camelCase JSON of `litemall_cj_sourcing_request`):
`{id, cjSourcingId, cjPid, productName, productImage, productUrl, remark, price, sourceStatus,
sourceStatusStr, cjProductId, cjVariantSku, addTime, updateTime, deleted}`.

## Semantics & decisions

- **Sourcing storage = dedicated table `litemall_cj_sourcing_request` (V32)**, mirroring the V28
  `litemall_cj_dispute` quartet (hand-written domain + mapper in `litemall-db`). Rejected the
  `litemall_system` key-value store — the admin list needs paging/filtering. The LOCAL table is
  the source for the admin list (survives restarts, renders CJ-down); CJ status is a lazily
  refreshed projection: `refresh=true` re-queries `product/sourcing/query` (≤20 rows per sweep,
  one paced CJ call) and patches `sourceStatus/sourceStatusStr/cjProductId/cjVariantSku`.
- **Create resolution:** `cjPid` fills `productName`/`productImage` from the local
  `litemall_cj_product` snapshot (live CJ detail fallback); CJ requires both, so a request with
  neither a known pid nor explicit name+image → errno 402, nothing created at CJ. A CJ-side
  create failure still persists the row with `sourceStatusStr: "create-failed: …"` (visible,
  not silently lost) and no `cjSourcingId`.
- **Videos = separate endpoint, NOT a field on `/srv/goods/detail`.** The detail
  `{goods, products, specifications, attributes, categoryIds}` shape is consumed verbatim by the
  SPA and has two build paths (DB snapshot + live); a separate lazy-loadable endpoint is
  zero-risk and mirrors the `/srv/comment` split. Accepts BOTH id forms like `/detail`
  (`cj_<pid>` or a native numeric id resolved via `source='cj'`+`cj_pid`). Non-CJ goods, no
  videos, or CJ down → `{errno:0, data:[]}` — never an error. Cached per-pid in Redis
  (`cj:raw:videos:<pid>`, 6h) behind the shared 1-QPS limiter; only `ON_STATE` videos are
  relayed.
- **⚠ Video URLs are CJ download URLs** (`download-only-api.cjdropshipping.com`): fetching the
  file requires a `Referer: https://developers.cjdropshipping.com/` header. A plain `<video src>`
  may be rejected — if so the SPA/edge needs a tiny proxy or the Referer set; we deliberately
  only relay the URL.
- **Warehouse quirk:** CJ's `warehouse/detail` envelope is `{code:200, result:true, ...}` (unlike
  the product API). A CJ-side miss (e.g. code 1608001 "Warehouse info not found") → errno 402
  with CJ's message; transport failure/timeout → errno 502 "CJ warehouse lookup unavailable".
  Cached per storageId (`cj:raw:warehouse:<id>`, 6h). storageIds come from variant inventory
  (`product/stock/queryByVid` → `areaId`) or CJ order payloads.
- **Detail enrichment with ship-from warehouse name/country: deferred** (optional in CLAUDE.md).
  Variant inventory already carries `countryCode`; grafting warehouse names onto the detail shape
  wasn't worth the contract risk. The admin lookup covers the operational need.

## Identity & security, gateway wiring

- `/srv/private/admin/cj/**` is ROLE_ADMIN via svcsecurity deny-by-default (NOT in
  `public-paths`). Through the gateway that means machine token + `X-User-Roles: ROLE_ADMIN`
  (+ `X-User-Id`), same as the other `/srv/private/admin/**` families. gateway-admin's existing
  goods-management route should already cover the path — verify, don't re-route.
- `/srv/goods/videos` rides the existing public `/srv/goods/**` exposure (anonymous read, no
  userId) and the existing gateway-api goods route — no new route needed.
- No caller-supplied identity anywhere; admin endpoints take no userId at all.
