# handoff-gateway-admin-flash-deals — admin flash-deal contract (Deals wave, Phase B)

Served by litemall-goods-management under `/srv/private/admin/deal/*` — rides
gateway-admin's existing goods catch-all route (machine token +
`X-User-Roles: ROLE_ADMIN`; nothing to add on the edge). Standard envelope
`{errno, errmsg, data}`.

> NOTE: the admin SPA (Promotion → Flash Deals: `DealList`/`DealForm`, RTK
> `adminDealApi`) was built in-worktree alongside this backend (Phase-A
> precedent), so this doc is the contract RECORD, not a work order.

Design background: `adr-flash-deals-price-swap.md`. The one concept the UI must
convey: **`enabled` is intent, `live` is state.** The lifecycle scheduler swaps the
deal price onto the goods row when an enabled deal's window opens and unwinds it
when the window closes / the deal is disabled or deleted / a capped deal sells out —
always within ~60s, never synchronously with the admin call.

## Deal view object (every read/list/create/update response)

```json
{
  "id": 3, "goodsId": 1009012, "goodsName": "…", "picUrl": "…",
  "dealPrice": 39.00,
  "originalRetailPrice": 59.00,   // null until first swap-on
  "stock": 5,                     // cap; <=0 = uncapped
  "sales": 2,                     // claimed (recomputed from paid orders each tick)
  "startTime": "2026-07-16T10:00:00", "stopTime": "2026-07-16T22:00:00",
  "enabled": true,                // status column (intent)
  "live": false                   // price_swapped column (state)
}
```

## Endpoints

| method+path | body / params | notes |
|---|---|---|
| `GET /srv/private/admin/deal/list` | `page` (1-based, default 1), `limit` (default 20) | `data:{total, page, limit, list:[view]}` — newest first |
| `GET /srv/private/admin/deal/read` | `id` | 402 badArgumentValue when missing |
| `POST /srv/private/admin/deal/create` | `{goodsId, dealPrice, startTime, stopTime, stock?}` | created enabled; times are ISO `LocalDateTime` (no zone suffix) |
| `POST /srv/private/admin/deal/update` | `{id, dealPrice?, startTime?, stopTime?, stock?, enabled?}` | partial; nulls = keep |
| `POST /srv/private/admin/deal/delete` | `{id}` | logical delete; a live swap unwinds next tick |

## Errno table (SPA renders errmsg inline; each names the offending field/reason)

| errno | when | UI treatment |
|-------|------|--------------|
| 650 | dealPrice ≤ 0 or ≥ goods retail; start ≥ stop; window entirely past; goods missing; **CJ: dealPrice below the cost floor (errmsg names the floor)** | inline form error |
| 651 | another ENABLED deal overlaps this goods+window; OR editing `dealPrice`/`startTime` while `live` | inline; for the live case the message says "disable it before changing price or start" — offer the disable toggle |
| 652 | CJ goods with NO captured wholesale cost (`litemall_goods.cost` empty — row not re-synced since V45): cost basis unknown, deal refused. **CJ deals are otherwise LIVE since Wave 12** (floored at the captured cost) | message inline; the deal action stays enabled for CJ goods |

## Validation rules the form should mirror (server is authoritative)

- `dealPrice` strictly below the goods' CURRENT retail price (for a live deal,
  below the captured `originalRetailPrice`); for CJ goods additionally at or
  above the captured wholesale cost (`litemall_goods.cost`).
- `startTime < stopTime`; `stopTime` in the future.
- One enabled deal per goods per overlapping window.
- While `live`: price/start locked; stop (extend), stock and enabled remain editable.

## Related customer surfaces (for cross-checking, not admin work)

- `GET /srv/goods/deal?id=` → `{dealPrice, originalPrice, endEpoch, stock, claimed,
  claimedPct}` or `data:null` — detail-page countdown block.
- Search DTO gains `dealActive`/`dealEndEpoch`/`dealClaimedPct` while live;
  `/srv/search?deal_flag=1&deal_active=1`, sort `deal_end_epoch` = "ending soon".
