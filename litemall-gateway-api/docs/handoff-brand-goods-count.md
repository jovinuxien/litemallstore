# Handoff — per-brand goods count on `/srv/brand/list`

**Owner:** litemall-goods-management
**Requested by:** gateway-api (customer SPA, "Shop by brand")
**Date:** 2026-07-08

## Problem

`GET /srv/brand/list` (LitemallBrandController → BrandQueryService) returns every
brand row unconditionally. The seed carries ~49 brands, but most native brand rows
have **no goods** pointing at them today — the catalog is dominated by CJ imports
whose `brandId`/`manufacturerId` does not map to these native brands. Measured live
(2026-07-08, `:8082`): of 12 sampled brands only 2 had any goods (MUJI=19,
Ralph Lauren=13); the rest returned `total: 0` for `/srv/goods/list?brandId=`.

Result: the customer "Shop by brand" directory shows brand tiles that dead-end at
*"No products listed for this brand yet."* on the majority of clicks.

`floorPrice` is **not** a usable signal — it is populated on all 49 brand rows from
seed data regardless of whether any goods exist. The OCS search index exposes no
brand/manufacturer facet either (facet fields: price, category_names, category_ids,
variant_price, source).

## Interim (already shipped, SPA-side)

`BrandList.tsx` now probes each brand's on-sale count via
`/srv/goods/list?brandId=X&limit=1` (reading `total`) and renders only brands with
`count > 0`, sorted by count desc, with an "N products" badge. This costs ~49 tiny
parallel requests on the `/brands` page load (cached for the session). Acceptable
for a directory page, but it is an N+1 that a backend count eliminates.

## Requested change (either is fine; option A preferred)

**Option A — add `goodsCount` to each brand row.** Include the count of on-sale,
non-deleted goods for the brand in the `/srv/brand/list` payload:

```json
{ "id": 1001000, "name": "MUJI Manufacturer", "picUrl": "...", "floorPrice": 12.90,
  "goodsCount": 19 }
```

The SPA already reads `goodsCount` if present and skips the probe when it is a number
(see `BrandList.tsx` `countFor`), so shipping this field is transparently picked up —
no SPA change needed to drop the probes, only to stop probing when the field exists.

**Option B — a filter param.** Support `GET /srv/brand/list?hasGoods=true` returning
only brands with ≥1 on-sale good. The SPA would switch to passing `hasGoods=true` and
drop the per-brand probes.

Count semantics should match `/srv/goods/list?brandId=` (on-sale, not deleted) so the
directory count and the brand page agree.

## Acceptance

- `/srv/brand/list` either carries a correct `goodsCount` per row, or honors
  `?hasGoods=true`.
- The count equals `/srv/goods/list?brandId=<id>` `total` for the same brand.
- gateway-api then drops the client-side probe (single follow-up in `BrandList.tsx`).
