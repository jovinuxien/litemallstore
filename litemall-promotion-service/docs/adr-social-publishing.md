# ADR — Social publishing (Meta FB/IG + TikTok), Wave 6

Status: accepted (design locked in `doc/social-email-marketing-plan-2026-07-16.pdf`).

## Context

Wave 6 adds outbound social posting to the promotion service: a manual admin
composer plus an opt-in auto-post on flash-deal activation, over Meta (Facebook
Page + Instagram business) and TikTok. Platform credentials are a USER-SIDE
prerequisite that may land mid-wave — everything must build, boot and be
verifiable without them.

## Decisions

### 1. Ledger-first, fail-soft (the Mautic conventions, exactly)

Every publish attempt is a `litemall_social_post` row (V42). Adapters implement
`SocialPublishPort` and NEVER throw: disabled adapter, missing credentials,
missing required media, transport error, and in-body API errors all come back
as a failed result → row `status='failed'` + `error`, log WARN, HTTP stays 2xx.
Status transitions are guarded in SQL (`draft|failed → posted|failed`); a
`posted` row is immutable history. Retry (`POST …/{id}/retry`) re-fires only
`failed` rows, reusing the stored caption/media/link.

### 2. Meta: one Graph-API app, two adapters

`infrastructure/acl/meta/` holds ONE Feign client (`MetaGraphClient`,
version-pinned base URL `graph.facebook.com/v19.0`) and two `SocialPublishPort`
adapters:

- **meta_fb** — `POST /{page-id}/photos` (image posts; the UTM share link is
  appended to the caption, photos have no link field) or `POST /{page-id}/feed`
  (no image → message + `link`, Meta renders the preview). External id =
  `post_id` (`<pageId>_<postId>`) when returned, else the object id.
- **meta_ig** — two-step: `POST /{ig-user-id}/media` (container, `image_url` +
  caption incl. link) then `POST /{ig-user-id}/media_publish`. Image REQUIRED —
  no image is an immediate failed row, and the composer greys IG out.

**Token model:** `litemall.promotion.social.meta.page-access-token` must be a
**long-lived PAGE access token** of the app's admin user with
`pages_manage_posts`, `pages_read_engagement` and `instagram_content_publish`.
Renewal runbook (tokens outlive 60 days only as page tokens):
1. Graph API Explorer (or app dashboard) → user token with the scopes above.
2. `GET /oauth/access_token?grant_type=fb_exchange_token&client_id=<app>&client_secret=<secret>&fb_exchange_token=<user-token>` → 60-day user token.
3. `GET /<page-id>?fields=access_token&access_token=<long-lived-user-token>` →
   page token (does not expire while the user token stays valid; regenerate on
   password change / scope change).
4. Put it in `SOCIAL_META_PAGE_TOKEN` env; `ig-user-id` =
   `GET /<page-id>?fields=instagram_business_account`.

### 3. TikTok: Content Posting API, direct post, PULL_FROM_URL, video-only

`infrastructure/acl/tiktok/TikTokContentClient` calls
`POST /v2/post/publish/video/init/` with
`source_info.source=PULL_FROM_URL` — TikTok fetches the goods' video itself, so
no upload streaming through our service. TikTok has no image/text post in this
API: **video is a hard gate**. The video comes from the Wave-3 goods-service
surface (`GET /srv/goods/videos?id=` — CJ product videos); a goods without one
is greyed in the composer and yields an honest failed row if forced.

TikTok reports logical failures inside a 200 body — the adapter checks
`error.code != "ok"`, not just transport. External id = `publish_id` (an async
processing handle; the API offers a status-fetch endpoint as future work).

**Privacy level:** default `SELF_ONLY` because TikTok restricts UNAUDITED apps
to self-only visibility; flip `litemall.promotion.social.tiktok.privacy-level`
to `PUBLIC_TO_EVERYONE` once the app passes TikTok's Content Posting audit.
`access-token` is a creator-scoped OAuth token with `video.publish`
(refresh via TikTok's standard OAuth refresh flow; out of scope here).

### 4. Auto-post dedupe — restart-safe, DB-state only

Requirement: at most one auto row per **(goods, platform, deal activation)**;
restart mid-window must not duplicate; deactivate → re-activate = a NEW
activation → a new post; no in-memory state.

Mechanism (two extra ledger columns, `deal_id` + `auto_active`):
- an auto row is inserted **armed** (`auto_active=1`) for its (deal, platform);
- each 60s tick first **disarms** armed rows whose deal is *currently not
  live* (`price_swapped=0`, window closed, disabled or deleted — pure current
  state, no transition memory), then posts only (live deal × enabled platform)
  pairs with no armed row.

Consequences, all deliberate:
- restart mid-window: armed rows exist → no duplicates;
- deactivate/re-activate: some tick observes the unwound state → disarm → the
  re-activation posts fresh rows (old rows stay in the ledger, disarmed);
- an unwind + re-swap that BOTH complete inside a single poll interval — or
  entirely during a promotion-service outage — is coalesced into the same
  activation (no second post). Accepted: the poll (60s) is far faster than the
  goods-management lifecycle tick that performs unwinds;
- a **failed** auto row stays armed: one attempt per activation, no 1-minute
  retry storm at a down platform; recovery is the admin Retry button;
- TikTok enabled + video-less deal goods ⇒ a visible failed row ("requires a
  video"), not a silent skip.

The auto-poster reads deals via `SocialCatalogPort` (over goods-management's
V38 `price_swapped` state — read-only, NO cross-module edit; goods-management's
`FlashDealLifecycleTask` remains the only lifecycle writer).

### 5. UTM share links

`utils/UtmShareLink` implements the Wave-6 shared convention
(`utm_source=facebook|instagram|tiktok`, `utm_medium=social`,
`utm_campaign=deal-<dealId>|goods-<goodsId>`) over
`litemall.promotion.social.share-base-url` + `/product/<goodsId>`. The link
rides the `link` param on FB feed posts and the caption on FB photo/IG posts;
TikTok direct posts carry no link (platform constraint — the row still stores
`link_url` for reference).

### 6. Layering

`application/ports`: `SocialPublishPort` (out), `SocialCatalogPort` (in),
`CustomerContactProvider` (in, Mautic email enrichment). ACLs under
`infrastructure/acl/{meta,tiktok}`; db access via
`infrastructure/repositories/impl` + `infrastructure/services`. Domain imports
no client (phase-3 boundary check still passes). Feign per module precedent —
no new RestTemplate.

## Manual verification once real credentials land

1. `SOCIAL_META_ENABLED=true SOCIAL_META_PAGE_ID=… SOCIAL_META_PAGE_TOKEN=…
   SOCIAL_META_IG_USER_ID=…` (+ TikTok equivalents) on the promotion service.
2. Composer → post to one platform at a time; expect `posted` + external id;
   confirm the post on the page/account; confirm the caption carries the UTM
   link and Matomo records a `utm_source=<platform>` campaign visit when
   clicked.
3. TikTok: pick a CJ goods with a video (`/srv/goods/videos?id=` non-empty);
   expect `publish_id` and the video in the creator's inbox/profile
   (SELF_ONLY until audited).
4. Auto-post: enable the flag, activate a flash deal, expect one `auto` row per
   enabled platform within a minute (already verified with adapters disabled —
   rows appear as `failed(disabled)`; with creds they should be `posted`).
