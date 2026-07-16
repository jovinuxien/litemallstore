# Handoff — social composer/admin contract (promotion → gateway-admin, Wave 6)

Backend for the gateway-admin "Promote" composer dialog, the "Social posts"
ledger page, and the retry button. All endpoints live on the promotion service
under `/srv/private/admin/social/**` — route them like the existing
coupon/groupon admin routes (machine-token relay + `X-User-Id` and
`X-User-Roles: ROLE_ADMIN` forwarded; svcsecurity's `/srv/private/admin/**`
rule gates them).

## Enums (contract values everywhere)

| field | values |
|---|---|
| `platform` | `meta_fb` \| `meta_ig` \| `tiktok` |
| `status`   | `draft` \| `posted` \| `failed` (rows are only ever visible as `posted`/`failed`; `draft` is a transient pre-publish state) |
| `postedBy` | admin id from `X-User-Id`, or the literal `auto` (deal auto-poster — render a badge) |

## `GET /srv/private/admin/social/compose-preview?goodsId=<id>`

`404` when the goods does not exist. `200`:

```json
{
  "goodsId": 1181000,
  "goodsName": "Alpine White Bookcase",
  "price": 49.00,             // current retail (already the deal price while a deal is live)
  "dealPrice": 29.00,         // null when no live flash deal
  "dealId": 3,                // null when no live flash deal
  "caption": "Flash deal: Alpine White Bookcase — now $29.00 (was $49.00). Limited time only!",
  "images": ["http://…/pic.jpg", "http://…/gallery1.jpg"],   // pic_url first, then gallery
  "videoUrl": null,           // null ⇒ the goods has no product video (gates TikTok)
  "platforms": [
    {"platform": "meta_fb", "displayName": "Facebook Page", "enabled": false,
     "available": true,  "reason": null,
     "shareUrl": "http://localhost:9000/product/1181000?utm_source=facebook&utm_medium=social&utm_campaign=deal-3"},
    {"platform": "meta_ig", "displayName": "Instagram", "enabled": false,
     "available": true,  "reason": null, "shareUrl": "…utm_source=instagram…"},
    {"platform": "tiktok",  "displayName": "TikTok", "enabled": false,
     "available": false, "reason": "TikTok requires a video — this goods has none",
     "shareUrl": "…utm_source=tiktok…"}
  ]
}
```

UI rules: prefill the caption editor with `caption`; media picker over `images`
(+ `videoUrl` in the preview pane when present); one checkbox per platform —
**disable the checkbox with `reason` as tooltip when `available=false`**, and
grey/hint when `enabled=false` (posting is still allowed; it just produces an
honest `failed` row while platform creds are absent — the acceptance path
before tokens arrive). `caption` never embeds the share URL — the backend
appends the right per-platform link at publish time.

## `POST /srv/private/admin/social/post`

```json
{"goodsId": 1181000, "caption": "…", "mediaUrl": "http://…/pic.jpg",
 "platforms": ["meta_fb", "meta_ig"]}
```

- `caption` blank ⇒ backend falls back to the templated caption.
- `mediaUrl` = the picked **image** (used by meta_fb/meta_ig; meta_fb without
  one falls back to a plain link post). TikTok ignores it and always publishes
  the goods' product video.
- `400 {"error": "…"}` only for caller mistakes (missing goodsId/platforms,
  unknown platform value); `404` for unknown goods.
- Otherwise **always `200`** with one result per requested platform — publish
  failures are data, not HTTP errors:

```json
{"results": [
  {"platform": "meta_fb", "postId": 12, "status": "failed", "externalPostId": null,
   "error": "Meta adapter disabled (litemall.promotion.social.meta.enabled=false)"},
  {"platform": "meta_ig", "postId": 13, "status": "posted", "externalPostId": "1789…", "error": null}
]}
```

Toast per entry (`postId` is the ledger row id). Error strings worth
special-casing in copy: `…adapter disabled…` (creds not landed yet),
`TikTok requires a video — this goods has none`,
`Instagram requires an image — pick one in the composer`.

## `GET /srv/private/admin/social/list?page=1&limit=20[&status=failed][&platform=tiktok]`

```json
{"total": 42, "page": 1, "limit": 20, "list": [
  {"id": 12, "goodsId": 1181000, "platform": "meta_fb", "caption": "…",
   "mediaUrl": "http://…", "linkUrl": "http://…utm_campaign=deal-3",
   "status": "failed", "externalPostId": null, "error": "Graph API error: …",
   "postedBy": "auto", "dealId": 3,
   "addTime": "2026-07-16T13:40:00", "updateTime": "2026-07-16T13:40:01"}
]}
```

Newest first. `status`/`platform` filters combine. LocalDateTime fields are
ISO strings (core JacksonConfig). Link-out when `externalPostId` is set:
meta_fb ids are `<pageId>_<postId>` → `https://www.facebook.com/<externalPostId>`;
meta_ig returns a media id (no stable public URL — show the id);
tiktok returns a `publish_id` (processing handle — show the id).

## `POST /srv/private/admin/social/{id}/retry`

Guarded to failed rows: `404` unknown id, `400 {"error":"only failed rows can be
retried"}` when the row is not `failed`, else `200` with a single result object
(same shape as one `results[]` entry). Retry re-fires the SAME row
(stored caption/media/link) — no new ledger row.

## Auto-post rows

With `litemall.promotion.social.auto-post-deals=true`, flash-deal activations
post automatically to every ENABLED platform: `postedBy="auto"`, `dealId` set,
`utm_campaign=deal-<dealId>`, at most one auto row per (deal, platform,
activation) — deactivating and re-activating a deal produces a NEW row.
Failed auto rows are retried only via the admin Retry button (never
automatically). Semantics: `docs/adr-social-publishing.md`.
