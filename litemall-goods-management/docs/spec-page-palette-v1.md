# spec-page-palette-v1 — DIY page palette, v1 (NORMATIVE)

Status: **normative** for gateway-api (customer renderer) and gateway-admin
(structured page editor). Owner: goods-management (content subdomain).
Wave 4, 2026-07-13. See also `handoff-content-endpoints.md` for the article
CMS endpoints and the full admin CRUD surface.

## 1. Model

A *page* is an ordered list of typed components ("palette v1"). Pages are
stored in `litemall_page` (V36):

| column     | type              | notes                                          |
|------------|-------------------|------------------------------------------------|
| `id`       | int PK            |                                                |
| `name`     | varchar(63)       | admin-facing label                             |
| `position` | `home` \| `custom`| exactly ONE `home` page may be `active` — enforced **in schema** by a stored generated column + UNIQUE index (not app logic) |
| `config`   | mediumtext        | palette v1 JSON, ≤ 64 KB (UTF-8 bytes)         |
| `status`   | `draft` \| `active` |                                              |

The page config JSON:

```json
{
  "version": 1,
  "components": [
    { "type": "banner", "key": "optional-stable-string", "config": { ... } }
  ]
}
```

- `version` MUST be `1`.
- `components` is an ordered array, **0–30 entries**; render order = array order.
- `key` is optional (client-side list key); server preserves it verbatim.
- Serialized `config` column value MUST be ≤ 65536 UTF-8 bytes.
- Components store **IDs and queries, never product snapshots** (deliberate
  divergence from crmeb: no stale denormalized goods data in the page row).

## 2. Component catalog (v1 — closed set)

Unknown `type` → validation errno 640. Machine-readable version of this
table: `GET /srv/private/admin/page/palette` (drives the structured editor).

### 2.1 `banner`
| config field | type   | required | notes                    |
|--------------|--------|----------|--------------------------|
| `image`      | string | YES      | image URL                |
| `link`       | string | no       | SPA route or absolute URL|
| `title`      | string | no       | overlay text             |

Static — no fetch at render time.

### 2.2 `image-row`
| config field | type | required | notes |
|---|---|---|---|
| `images` | array of `{image: string REQUIRED, link?: string}` | YES, 1–8 items | |

Static — no fetch.

### 2.3 `goods-list`
| config field | type | required | notes |
|---|---|---|---|
| `mode` | `byIds` \| `byCategory` \| `hot` \| `new` \| `deals` | YES | `deals` added in v1.1 |
| `goodsIds` | int[] (1–24) | when `mode=byIds` | |
| `categoryId` | int | when `mode=byCategory` | |
| `limit` | int 1–24 | no (default 8) | ignored for `byIds` |
| `title` | string | no | section header |

**Resolution (client-side, by the renderer):**
- `byIds` → `POST /srv/goods/batch`, JSON body = the id array (e.g. `[1181000,1181001]`).
  **Envelope: raw `Map<goodsId, goodsAggregate>` — NO `{errno,data}` wrapper.**
  Missing/off-sale ids are simply absent from the map. Renderer preserves the
  configured `goodsIds` order.
- `byCategory` → `GET /srv/goods/list?categoryId={categoryId}&limit={limit}&page=1`.
  **Envelope: `{errno, data:{list, total, ...}}`.**
- `hot` → `GET /srv/goods/list?isHot=true&limit={limit}&page=1` — same envelope.
- `new` → `GET /srv/goods/list?isNew=true&limit={limit}&page=1` — same envelope.
- `deals` (v1.1) → `GET /srv/search?deal_flag=1&size={limit}&page=1`.
  **Envelope: `{errno, data:{goodsList, total, ...}}` (the search envelope —
  NOT `data.list`).** Items carry `discountPct` for the "% off" badge.
  Ordering is the searcher's scored browse (discount depth × popularity ×
  rating multiplied into relevance) — deepest/most-popular deals first.
  Serves local AND CJ goods (unlike `hot`/`new`) — though CJ goods index
  `discount_pct=0` today, so they appear only if a future CJ markdown
  source sets one. Degrade rule R1 applies: search down or zero deals ⇒
  strip skipped, never an error.

> **Documented divergence:** `isHot`/`isNew` are local-DB signals with no OCS
> index field, so `hot`/`new` modes serve **local goods only** (CJ-sourced
> goods never appear), unlike the OCS-boosted legacy home rails. Accepted for
> palette v1.

### 2.4 `coupon-strip`
| config field | type | required | notes |
|---|---|---|---|
| `limit` | int 1–10 | no (default 3) | **renderer slices** — see below |
| `title` | string | no | |

Resolution: `GET /srv/promotion/coupon/available`.
**Envelope: BARE JSON ARRAY of coupon DTOs. The endpoint takes NO limit/page
params — the renderer slices the array to `limit` itself.**

### 2.5 `seckill-strip`
| config field | type | required | notes |
|---|---|---|---|
| `limit` | int 1–10 | no (default 3) | renderer slices |
| `title` | string | no | |

Resolution: `GET /srv/promotion/seckill/active`.
**Envelope: BARE JSON ARRAY, NO limit param — renderer slices.**

### 2.6 `article-strip`
| config field | type | required | notes |
|---|---|---|---|
| `limit` | int 1–10 | no (default 3) | server-side param — this endpoint DOES page |
| `hotOnly` | boolean | no (default false) | filter to `is_hot` articles |
| `title` | string | no | |

Resolution: `GET /srv/article/list?page=1&limit={limit}[&hotOnly=true]`.
**Envelope: `{errno, data:{list, total}}`** (goods-management standard
`ResponseUtil.okList` shape). Published articles only, newest first.

### 2.7 `rich-text`
| config field | type | required | notes |
|---|---|---|---|
| `html` | string | YES | sanitized SERVER-side on save (jsoup custom Safelist, clean-and-store — `Jsoup.clean` never rejects, it strips). Renderer may inject `data.html` directly. |

Static — no fetch.

## 3. Degrade rule R1 (renderer contract)

> **R1: a component with no resolvable data is SKIPPED, never an error.**

- Any strip/goods-list fetch that fails (network, 5xx, promotion service
  down) or returns an empty list ⇒ the renderer omits that component and
  renders the rest. The page must never be broken by one dead component.
- Static components (banner, image-row, rich-text) always render.

## 4. Customer endpoints (gateway-api)

Both prefixes are on goods-management's `public-paths` (anonymous OK).

### `GET /srv/page/home`
- Active home page exists → `{errno: 0, data: <PageView>}`.
- No active home → `{errno: 642, errmsg: "no active home page"}` ⇒ the SPA
  **falls back to the legacy hardcoded home**. No active home is seeded — the
  legacy home stays pixel-identical until an admin activates a page.

### `GET /srv/page/{id}` (numeric id)
- Page exists, `status=active` → `{errno: 0, data: <PageView>}`.
- Missing, deleted, or draft → `{errno: 642, errmsg: "page not active"}`.
  (Drafts are never served customer-side — preview goes through the admin
  read endpoint.)

### PageView shape
```json
{
  "id": 7,
  "name": "Summer Sale",
  "position": "custom",
  "components": [ { "type": "...", "key": "...", "config": { ... } } ],
  "updateTime": "2026-07-13T10:00:00"
}
```
`components` is the validated stored array — the renderer resolves data per
§2 and applies R1.

## 5. Admin surface (gateway-admin) — summary

Full detail in `handoff-content-endpoints.md`. All under
`/srv/private/admin/page/**` (machine token + `X-User-Roles: ROLE_ADMIN`).

| endpoint | notes |
|---|---|
| `GET /list?position=&status=&page=&limit=` | `{errno,data:{list,total}}` |
| `GET /read?id=` | any status (this is draft preview) |
| `GET /palette` | machine-readable §2 schema — build the editor from THIS, not hardcoded forms |
| `POST /create` | `{name, position, config}` → always created as `draft`; palette validated → 640 |
| `POST /update` | `{id, name?, config?}` → re-validates → 640 |
| `POST /activate` | `{id}` — transactional home swap: activating a `home` page demotes the current active home in the same TX; concurrent-activation race → 641 |
| `POST /deactivate` | `{id}` |
| `POST /delete` | `{id}` — refuses the active home → 641 |

## 6. Errno registry (640–643, content subdomain)

| errno | name | meaning |
|---|---|---|
| 640 | `PAGE_CONFIG_INVALID` | palette validation failure; `errmsg` NAMES the offending component, e.g. `component[2] (goods-list): mode=byIds requires goodsIds` |
| 641 | `CONTENT_CONFLICT` | activate race lost; delete refused on active home; article-category delete refused while referenced |
| 642 | `PAGE_NOT_ACTIVE` | no active home / requested page not active |
| 643 | `ARTICLE_NOT_AVAILABLE` | article missing or hidden (customer read) |

(611 = GOODS_NAME_EXIST, 622 = reply-exists, 631/632 = stock — unrelated,
listed to prevent re-minting.)

## 7. Validation summary (server-side, on create/update)

Errno 640 with a component-naming message for ANY of:
- `version != 1`; `components` missing or not an array; > 30 components
- serialized config > 64 KB
- unknown component `type`
- missing required config per §2 (incl. conditional: `byIds`→`goodsIds`,
  `byCategory`→`categoryId`)
- wrong scalar types / out-of-range limits / empty `images`

`rich-text.html` is NOT rejected for markup — it is sanitized in place
(clean-and-store) before persisting.
