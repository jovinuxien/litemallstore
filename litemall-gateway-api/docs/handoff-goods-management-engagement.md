# Handoff spec — engagement verticals the customer SPA already calls

- **Producer (to build):** `litemall-goods-management` (Wave-2 "engagement verticals" task)
- **Consumer (already wired, guarded):** `litemall-gateway-api` customer SPA
- **Routing:** already landed on the customer gateway — the `customer-engagement` route sends
  `/srv/collect/**`, `/srv/footprint/**`, `/srv/feedback/**`, `/srv/comment/**` to
  `lb://litemall-goods-management`. Machine token + `X-User-Id` identity forwarding fire on it.

Every endpoint below is **already called by the SPA** behind an `isMissingEndpoint` (404/501)
guard — ship the endpoint and the page goes live with no SPA change. Identity is ALWAYS the
gateway-injected `X-User-Id` header; no caller-supplied `userId` anywhere (cart-IDOR rule).
Envelope: the standard `{errno, errmsg, data}` — the SPA unwraps `data`.
The litemall-db layer (domain `LitemallCollect/Footprint/Feedback/Comment`, mappers, services)
already exists — no new migrations expected.

Call sites (exact request shapes): `src/main/webapp/app/shared/api/userApi.ts`; consuming views
named per section.

## 1. Collect (favorites)

| Endpoint | Request | Response `data` |
|---|---|---|
| `GET /srv/collect/list?type=0&page=1&limit=30` | type 0 = goods (1 = topics reserved) | `{ list: CollectItem[], total }` — the view also tolerates a bare array or `{collectList}` |
| `POST /srv/collect/addordelete` | `{ type: 0, valueId }` — `valueId` is the goods id (number, or `cj_<pid>` string for CJ-sourced goods) | toggle semantics: collected → removed, not collected → added |

`CollectItem` fields the view reads (`modules/user/Favorites.tsx:9-17`): `id`, `valueId`,
`name|goodsName`, `brief`, `picUrl`, `retailPrice|price` (number or `{amount}`).
`CollectButton` on the product page calls the toggle optimistically and reverts on a real error.

## 2. Footprint (browsing history)

| Endpoint | Request | Response `data` |
|---|---|---|
| `GET /srv/footprint/list?page=1&limit=30` | newest first | `{ list: FootprintItem[], total }` (bare array tolerated) |
| `POST /srv/footprint/record` | `{ goodsId }` (number or `cj_<pid>`) | fire-and-forget from the product page on every view — **dedupe same user+goods per day server-side** (upstream behavior) |
| `POST /srv/footprint/delete` | `{ id }` | remove one row |

`FootprintItem` fields read (`modules/user/Footprint.tsx:9-17`): `id`, `goodsId`, `goodsName|name`,
`brief`, `picUrl`, `retailPrice|price`, `addTime`.

## 3. Feedback

| Endpoint | Request | Response `data` |
|---|---|---|
| `POST /srv/feedback/submit` | `{ content, mobile, type }` — `type` ∈ `feedback\|complaint\|bug\|other` (`modules/user/Feedback.tsx:8-13`) | ack only |

Admin list mirror (`/srv/private/admin/feedback/list`) is the goods-management block's own item.

## 4. Comment write path (product review)

| Endpoint | Request | Response `data` |
|---|---|---|
| `POST /srv/comment/post` | `{ type: 0, valueId, star, content, hasPicture?, picUrls?: string[] }` (`ICommentPost`, `userApi.ts`) | ack; the new row must then come back from `GET /srv/comment/list?valueId=` |

- `valueId`: goods id — numeric for local goods, `cj_<pid>` for CJ-sourced (same key
  `/srv/comment/list` already accepts). `star` 1..5. `content` ≤ 1023 chars (SPA-limited).
- Authenticated: bind the reviewer from `X-User-Id`; populate `userInfo{nickName,avatarUrl}` in
  the list projection from the user row.
- The purchase check (v1 `hasPurchased=false` vs order-facade verification) is the
  goods-management block's documented decision — the SPA sends no purchase proof.
- Callers: `ReviewForm` mounted on the product page (`Reviews.tsx` "Write a review") and on
  order detail per goods line when `handleOption.comment` is true (the "Unrated" flow).

## Acceptance echo (what the SPA will show once live)

- Favorites page lists/removes; heart button on the product page toggles.
- Footprint page lists visits recorded by browsing product pages (deduped per day).
- Feedback form submits and confirms.
- Submitting a review from product page or an Unrated order makes it appear in the product's
  review list (which is already live and unguarded in the SPA).
