# handoff-content-endpoints — Wave 4 goods-management surfaces

Companion to the **normative** `spec-page-palette-v1.md`. Everything here is
served by litemall-goods-management (`lb://litemall-goods-management`).
Standard envelope unless stated otherwise: `{errno, errmsg, data}`
(`ResponseUtil`); list payloads are `data:{list, total, page?, limit?, pages?}`.

Identity rules (unchanged):
- Customer writes need gateway-injected `X-User-Id` (never caller-supplied).
- `/srv/private/admin/**` needs machine token + `X-User-Roles: ROLE_ADMIN`.
- Anonymous customer reads are on `litemall.svcsecurity.public-paths`. NEW
  entries this wave: `/srv/region/**`, `/srv/storage/fetch/**`,
  `/srv/article/**`, `/srv/page/**`.

## Gateway routing checklist

- **gateway-api:** `/srv/region/**`, `/srv/storage/**`, `/srv/article/**`,
  `/srv/page/**` → `lb://litemall-goods-management` (`/srv/search/**`,
  `/srv/comment/**`, `/srv/topic/**` presumably already routed — verify).
  Machine-token relay on each new route. `POST /srv/storage/upload` requires
  a logged-in customer (X-User-Id) — do NOT add it to any public/anonymous
  bypass; `GET /srv/storage/fetch/**` is anonymous.
- **gateway-admin:** verify `/srv/private/admin/**` → goods-management covers
  `topic`, `history`, `comment/reply`, `article`, `page` (it already routes
  the admin prefix wholesale — then nothing to do).

---

## 1. Article CMS

### Customer (anonymous, public-paths)

#### `GET /srv/article/list?page=1&limit=10[&categoryId=][&hotOnly=true]`
Published, non-deleted articles, newest first. `data:{list,total}`; list item:
```json
{ "id": 1, "categoryId": 2, "categoryName": "News", "title": "...",
  "summary": "...", "picUrl": "...", "isHot": false, "isBanner": false,
  "goodsId": 0, "viewCount": 12, "addTime": "2026-07-13T10:00:00" }
```
(No `content` in list rows — MEDIUMTEXT stays on detail.)

#### `GET /srv/article/detail?id=`
Full article incl. `content` (server-sanitized HTML — safe to inject).
Increments `viewCount` atomically per hit (accepted anonymous
GET-with-UPDATE, crmeb-same). Missing / hidden / deleted → **errno 643**.

#### `GET /srv/article/categories`
`data:{list,total}` of `{id, name, sortOrder}` — non-deleted, by sortOrder.

### Admin (`/srv/private/admin/article`)

| endpoint | body / params | notes |
|---|---|---|
| `GET /list?title=&categoryId=&status=&page=&limit=` | | ALL statuses incl. hidden |
| `GET /read?id=` | | raw row (content included) |
| `POST /create` | `{categoryId, title, summary?, picUrl?, content, status?, isHot?, isBanner?, goodsId?}` | content sanitized (jsoup custom Safelist, clean-and-store); status defaults `published`; unknown categoryId → badArgumentValue |
| `POST /update` | `{id, ...same}` | re-sanitizes content |
| `POST /delete` | `{id}` | soft delete |
| `GET /category/list` | | incl. per-category article count |
| `POST /category/create` | `{name, sortOrder?}` | |
| `POST /category/update` | `{id, name?, sortOrder?}` | |
| `POST /category/delete` | `{id}` | **refused while articles reference it → errno 641** |

`status` values: `published` | `hidden`. Hidden articles stay in admin lists,
404-equivalent (643) customer-side.

## 2. DIY pages

Normative schema + customer endpoints + errno table: `spec-page-palette-v1.md`
§4–§7. Admin surface summary (§5): `/srv/private/admin/page/`
`list | read?id= | palette | create | update | activate | deactivate | delete`.
Editor MUST be built from `GET /palette` (machine-readable component schema).
A draft "Default Home" (empty components) is seeded by V36; nothing is active
until an admin activates — customer `/srv/page/home` returns 642 and the SPA
keeps rendering the legacy home.

## 3. Region cascade (Task A.1)

Anonymous (`/srv/region/**` public). Backed by an in-memory tree built lazily
from `litemall_region` (3231 rows; second call does no SQL).

- `GET /srv/region/list?pid=0` → `data` = BARE ARRAY of
  `{id, pid, name, type, code}` (upstream WxRegionController shape — no
  `{list,total}` wrapper); `pid=0` → the 31 provinces.
- `GET /srv/region/clist` → 3-level nested tree (legacy `RegionVo` shape):
  ```json
  { "errno":0, "data": [ { "id":1, "name":"北京市", "code":110000,
      "children":[ { "id":32, "name":"市辖区", "code":110100,
        "children":[ {"id":33,"name":"东城区","code":110101} ] } ] } ] }
  ```
  Note: `data` here is the province array (list, no total). Data is
  China-only → the SPA address cascade is optional (R1-style: hide if empty).
- DB down at first hit → clean `{errno:502}` payload, no poisoned cache.

## 4. Customer upload + public fetch (Task A.2)

- `POST /srv/storage/upload` — multipart `file`. Requires `X-User-Id` →
  otherwise **errno 501** (unlogin), nothing stored. Caps: 5 MB
  (`litemall.customer-storage.max-size-bytes`); image-only whitelist verified
  by **magic bytes** (jpeg/png/gif/webp), NOT Content-Type → violations get a
  clean 4xx envelope. Response `data`: `{key, name, type, size, url}` — `url`
  is anonymous-fetchable through the gateway
  (`http://localhost:8090/srv/storage/fetch/{key}`).
- `GET /srv/storage/fetch/{key:.+}` — anonymous binary read (public-paths).
  `../` in key → 400; unknown key → 404. The `{key:.+}` matters — without it
  Spring truncates the file extension.
- NOTE: `litemall.storage.local.address` previously pointed at a route that
  never existed; it now points at the gateway fetch URL, so NEW admin uploads
  also mint working URLs. Pre-existing stored URLs were already dead — no
  back-compat route.

## 5. Topic admin CRUD (Task A.3)

`/srv/private/admin/topic/` — AdminBrandController shapes:
`GET /list?title=&subtitle=&page=&limit=&sort=&order=` ·
`POST /create` (`{title, subtitle?, price?, picUrl?, sortOrder?, goods?, content?}`) ·
`GET /read?id=` · `POST /update` · `POST /delete` (`{id}`) ·
`POST /batch-delete` (`{ids:[...]}`). Round-trips to customer `/srv/topic/list`.

## 6. Search history (Task A.4)

- Recording: `GET /srv/search?q=` logs `{userId, keyword}` when the gateway
  forwarded an `X-User-Id`; anonymous searches write nothing; a history-write
  failure NEVER fails the search (soft-fail); consecutive duplicate keyword is
  deduped (latest-row check). No new endpoint — recording is a side effect.
- `GET /srv/search/index` already returns `historyKeywordList` for the user.
- `POST /srv/search/clearhistory` — `X-User-Id` required (501 otherwise);
  clears the caller's history.
- Admin: `GET /srv/private/admin/history/list?userId=&keyword=&page=&limit=`.

## 7. Comment reply (Task A.5)

- `POST /srv/private/admin/comment/reply` — `{commentId, content}`. Sets
  `admin_content` ONCE; a second reply → **errno 622** (reply exists — note:
  622, not upstream's 620-family text). Customer comment list already emits
  `adminContent` — zero read-side changes needed.

## 8. Freight tempId (Task C) — DEFERRED (2026-07-13)

Order's litemall-db edit mapping `LitemallGoods.tempId` exists on
`fix/order` (`42aded890`, confirmed: temp_id/weight/volume mapped) but had
NOT merged to master when this branch closed, and goods-management must not
edit `LitemallGoods` itself (order owns that edit — merge-conflict rule).

**Follow-up (small, post-merge):** once `fix/order` merges, round-trip
`tempId` through admin `GoodsAllinone` create/update/detail
(`AdminGoodsService`) and include it in `/srv/goods/goodsdetail` (order's
facade cherry-picks JsonNode fields — additive, non-breaking). Grey/ignore
the field for `source='cj'` goods. No migration — `litemall_goods.temp_id`
has existed since V2 (DEFAULT 0 = unbound).
