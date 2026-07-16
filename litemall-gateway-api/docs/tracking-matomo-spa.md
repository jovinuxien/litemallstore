# Matomo SPA tracking + customer reset mail (Wave-6 gateway-api)

Consumes promotion's Matomo deploy (`docker-compose/docker-compose.marketing.yml`,
their `docs/handoff-matomo-tracker.md`). Everything here ships **off by
default**: with no config the served SPA is byte-identical (no matomo script,
no `_paq`, zero tracker requests) and reset mail keeps the dev no-op sender.

## Task A — tracker

Config (edge yml / ENV, surfaced to the browser via `GET /auth/site-config`):

| key | ENV | dev value |
|---|---|---|
| `litemall.tracking.matomo.base-url` | `LITEMALL_TRACKING_MATOMO_BASE_URL` | `http://localhost:8095` |
| `litemall.tracking.matomo.site-id` | `LITEMALL_TRACKING_MATOMO_SITE_ID` | `1` ("litemall storefront") |
| `litemall.tracking.matomo.goods-dimension` | — | `1` (default) |

`/auth/site-config` → `{errno:0, data:{matomoUrl, matomoSiteId,
matomoGoodsDimension}}`; both null when unconfigured. The SPA
(`app/shared/tracking/matomo.ts` + `MatomoTracker.tsx`, mounted in App.tsx):

- fetches site-config once; unconfigured or Do-Not-Track ⇒ hard no-op
  (page views fired before the fetch resolves are buffered, then flushed or
  dropped);
- tracks every route change as a page view (`setCustomUrl` +
  `setReferrerUrl` chain);
- `/product/:id` page views carry the route's goods id (native id or
  `cj_<pid>`) as custom dimension `goods-dimension` — tracked in
  MatomoTracker, NOT Detail.tsx, so exactly one view per navigation;
  non-product views `deleteCustomDimension` so the id never leaks;
- auth pages (`/login`, `/register`, `/reset`) are tracked **path-only**
  (no query string) and no form/content tracking features are enabled;
- campaign attribution is Matomo-native: the tracked URL keeps its
  `utm_*` params (convention: `utm_source=facebook|instagram|tiktok`,
  `utm_medium=social`, `utm_campaign=<slug>` — SPA-side builder in
  `app/shared/util/campaignLink.ts`, byte-compatible with promotion's
  server-side composer links). `?invite=` and `utm_*` coexist: verified
  both captured, routing unaffected.

**Matomo-side dependency (promotion's provisioning):** goods-id recording
needs the bundled `CustomDimensions` plugin activated and an **action-scope
dimension at index 1** active on site 1. Until then Matomo accepts the hit
but stores `custom_dimension_1 = NULL` (verified 2026-07-16: beacon carries
`dimension1=<goodsId>`, visit + campaign recorded, dimension dropped —
plugin not yet activated). Also note Matomo drops visits from headless/bot
user agents — use a real UA when smoke-testing.

## Task B — reset mail

`SmtpResetMailSender` (edge-local JavaMailSender) binds when
`litemall.customer-mail.enabled=true`; the `LoggingResetMailSender` no-op
stays bound otherwise (both conditions are property-gated, deterministic).
Same `litemall.customer-mail.*` keys as litemall-core's CustomerMailSender
(order side) — shared ENV contract, zero shared code. Outer gate
`litemall.auth.reset-mail.enabled` unchanged (disabled ⇒ 701 + SPA hides the
forgot tab); anti-enumeration semantics untouched (send failures swallowed,
unknown email ⇒ errno 0). Template key `password-reset`, plain-text English.

Dev verify (2026-07-16, all green): MailHog on :1025/:8025 + both flags ⇒
mail with raw token delivered, confirm resets the password, token is
single-use (reuse ⇒ 703), old password revoked; defaults ⇒ 701 and no SMTP
bean.
