# Follow-up (goods-management) — `/srv/search` must surface the faceted contract the customer SPA consumes

**Raised by:** `fix/gateway-api` worktree (customer SPA). **Owner:** `fix/goods-management`
worktree. **Scope rule:** gateway-api must not edit goods-management; this records the
backend gap so it is actionable when the goods-management branch is picked up.

## TL;DR

The customer Search page (`app/modules/search/Search.tsx` + `app/modules/product/searchSlice.ts`)
is coded against a faceted `/srv/search` contract. The `LitemallSearchController` on
`fix/gateway-api` is a thin `q/offset/limit` passthrough that returns the **raw OCS
response verbatim**. As a result, against this branch's backend the SPA can populate
**autocomplete only** — the result grid, paging, facets, filtering and server-side sort
do not work. No SPA change is needed; the SPA degrades safely (empty rail, no crash)
until goods-management surfaces the contract below.

The richer mapping likely already exists on `fix/goods-management` (classes
`SearchService` / `OcsSearchResult` / `ProductDocument`, reportedly verified live against
238 goods) but is **not merged into `fix/gateway-api`** — it ships the early MVP
(`OcsSearchClient` / `OcsGoodsDocumentMapper` / `OcsProductDocument`). Preferred fix:
**land the goods-management OCS work onto this branch, then verify the param plumbing
(#1) below.**

## Routing (already correct — no change)

`litemall-gateway-api/src/main/resources/config/application.yml` routes the `/srv/**`
catch-all (last among `/srv/**` routes) to `lb://litemall-goods-management`, so both
`/srv/search` and `/srv/suggest` reach `LitemallSearchController`. The machine-token
relay fires on the `lb://` route. Nothing to change here.

## What the SPA sends

`searchSlice.buildQuery` issues:

```
GET /srv/search?q=<term>&category=<id>&brand=<csv of ids>&minPrice=<n>&maxPrice=<n>&sort=<price_asc|price_desc>&offset=<n>&limit=<n>
```

- `q` is always present (possibly empty).
- `category` — single category id (level-1 or level-2).
- `brand` — comma-separated brand/manufacturer ids (multi-select).
- `minPrice` / `maxPrice` — numeric bounds; omitted when blank.
- `sort` — omitted for relevance; otherwise `price_asc` / `price_desc`.
- `offset` / `limit` — paging (`offset = (page-1)*size`).

Autocomplete (works today): `GET /srv/suggest?q=<prefix>` → JSON array of strings.

## What the SPA expects back

A flat JSON map (the SPA tolerates an `{errno,errmsg,data}` envelope OR a raw map):

```jsonc
{
  "total": 137,            // total matches (drives paging: pages = ceil(total/size))
  "offset": 0,
  "limit": 12,
  "goodsList": [ /* IGood-shaped items, see field map below */ ],
  "facets": {
    "categories": [ { "id": 1005000, "name": "Women", "count": 42 } ],
    "brands":     [ { "id": 1001,    "name": "Acme",  "count": 12 } ],
    "price":      { "min": 0, "max": 999 }     // or null
  }
}
```

### `goodsList` item field map (OCS document → IGood)

`ProductCard` / `IGood` read the litemall field names, **not** the OCS snake_case ones.
Map each OCS hit document accordingly:

| OCS doc field (`OcsProductDocument`) | → SPA `goodsList` item (`IGood`) | Notes |
|---|---|---|
| `product_id` | `id` (number) | parse to int |
| `title` | `name` | |
| `image_url` | `picUrl` | |
| `price` | `retailPrice` (number) | current price |
| `discount_price` | `counterPrice` (number) | "was" price; card shows a strike-through + % when `counterPrice > retailPrice` |
| `description` | `brief` | optional |
| `brand` | (resolve to name for the brand facet) | currently indexed as `manufacturerId` **string**, not a name — resolve via `LitemallManufactureRepository` so the brand facet shows names |
| `category_names` / `category_ids` | feed the category facet | `category_names` is empty in the current indexer mapper; `category_ids` is a single id, not a breadcrumb |

Optional but used by the card if present: `salesQuantity` (→ "sold N"), `isNew`/`isHot`,
`star`, `isFreeShipping`.

## The three gaps to close (goods-management)

1. **Bind + translate the filter/sort params.** `search(...)` currently binds only
   `q/offset/limit`; add `category`, `brand` (csv), `minPrice`, `maxPrice`, `sort` and
   translate them to OCS filter/sort query params (OCS supports facet filters,
   comma multi-select, and `sort=-field` desc — see the `goods-management` capabilities
   note). Without this, every search returns the same unfiltered page.

2. **Map the OCS `SearchResult` → the flat envelope above.** OCS returns a nested
   `SearchResult` (slices → hits → `document.data`) with `matchCount` for the total;
   project it to `{ total, offset, limit, goodsList[] }` using the field map. Today
   `OcsSearchClient` returns the raw `Map` and nothing maps it, so `goodsList`/`total`
   never bind and the grid is empty / cards blank.

3. **Extract OCS facet buckets → `facets`.** Project the OCS facets (category, brand,
   price range) into `facets: { categories[], brands[], price }`. Today nothing emits
   `facets`, so the entire left rail renders empty (`No subcategories` / `No brands`).

## Acceptance (how the SPA will prove it landed)

With goods-management serving the contract and the OCS stack up:
- `GET /srv/search?q=phone&size=12` → non-empty `goodsList` with `id/name/picUrl/retailPrice`
  populated; cards render with names, images and prices; pager appears when `total > size`.
- Adding `&category=<id>` / `&brand=<id>` / `&minPrice=&maxPrice=` narrows `total` and
  the returned list.
- `&sort=price_asc` returns ascending prices across pages (removes the SPA's interim
  client-side sort dependency).
- The response `facets.categories` / `facets.brands` / `facets.price` are non-empty, so
  the Search left rail shows selectable category/brand options and the price range hint.

## SPA side (already done on `fix/gateway-api`, for reference)

- `app/modules/search/Search.tsx` — themed faceted page; URL is the single source of
  truth for `q/category/brand/minPrice/maxPrice/sort/page/size`.
- `app/modules/product/searchSlice.ts` — sends the params above; reads `goodsList`/`total`
  and `facets` defensively (empty-safe); keeps an interim client-side sort until #1 lands.
- `app/shared/model/search/search.models.ts` — `ISearchFacets` / `IFacetBucket` /
  `IPriceRange` / `SearchSort` define the agreed shapes consumed here.
- Suggest path (`/srv/suggest`) is already aligned and working.
