# Handoff — Matomo tracker env contract (promotion → gateway-api, Wave 6)

The promotion worktree deployed Matomo (Task A) and owns the analytics ACL that
reads it back. This note is the contract the **gateway-api** worktree consumes
for the SPA tracker bootstrap, plus the values of the live dev instance.

## Live dev instance (docker-compose/docker-compose.marketing.yml)

| what | value |
|---|---|
| Matomo UI / tracker base URL | `http://localhost:8095/` (override port via `MATOMO_PORT`) |
| Site | **"litemall storefront"** — `idSite=1`, main URL `http://localhost:9000`, ecommerce ON, timezone UTC |
| Superuser | `admin` (email `admin@litemall.local`) — password in the git-ignored `docker-compose/.env.marketing` |
| Reporting auth token | app token "litemall-wave6" minted for the `admin` user — actual value in the git-ignored `docker-compose/.env.marketing`; mint a fresh one per environment via *Admin → Security → Auth tokens* or `UsersManager.createAppSpecificTokenAuth` |

Credentials/tokens are deliberately NOT committed — the dev instance's actual
values live in `docker-compose/.env.marketing` (git-ignored, on the dev host).

The stack is OPT-IN: `docker compose -f docker-compose/docker-compose.marketing.yml up -d`
(see `docker-compose/README-DOCKER-COMPOSE.md`). Data survives `down` on the
named volumes.

## Env contract for the SPA tracker (gateway-api side)

The SPA injects the standard Matomo JS tracker ONLY when both values are
configured (absent ⇒ no script tag, byte-identical behavior):

```
MATOMO_TRACKER_URL=http://localhost:8095/   # base URL; the SPA loads {url}matomo.js and posts to {url}matomo.php
MATOMO_SITE_ID=1
```

(The names above are the recommendation — gateway-api owns its own config
plumbing; keep the two-value shape and the absent-⇒-disabled rule.)

Requirements from the Wave-6 block (verified against this instance):
- track SPA route changes as page views (`setCustomUrl` + `trackPageView`);
- product detail fires a page view carrying the **goods id as a custom
  dimension** — create the dimension once in Matomo (*Admin → Websites →
  Custom dimensions*, scope "action", e.g. name `goodsId`) and reference its
  id via `setCustomDimension`;
- honor Do-Not-Track (Matomo-side setting is already ON — installer default);
- auth pages: page-URL tracking only, never form contents.

## Promotion-service side (already coded, Phase 3 — config-only enablement)

The statistics ACL reads the Reporting API with the same values:

```
MATOMO_ENABLED=true
MATOMO_BASE_URL=http://localhost:8095
MATOMO_AUTH_TOKEN=<token from docker-compose/.env.marketing>
MATOMO_SITE_ID=1
PROMOTION_STATS_SOURCE=matomo   # or composite (order + matomo merge)
```

All four remain default-off/empty in committed yml (`litemall.promotion.matomo.*`,
`litemall.promotion.stats.source=order`). Flip to `composite` only once the SPA
tracker has produced data — an empty Matomo population contributes nothing and
the order source remains the transactional truth
(`docs/phase3-marketing-stack-integration.md`).

## Shared UTM convention (both sides of the link)

Social share links (and any campaign link) carry:

```
utm_source=facebook | instagram | tiktok
utm_medium=social
utm_campaign=<slug>        # deal-<dealId> for flash-deal posts, goods-<goodsId> otherwise
```

Builder on the service side: `org.linlinjava.litemall.promotion.utils.UtmShareLink`
(share base URL from `litemall.promotion.social.share-base-url`, default
`http://localhost:9000`; product path `/product/<goodsId>`). Matomo picks the
`utm_*` params up natively as campaign parameters — nothing to configure. The
SPA must let `utm_*` params coexist with the affiliate `?invite=` stash without
breaking route matching (gateway-api Wave-6 acceptance).
