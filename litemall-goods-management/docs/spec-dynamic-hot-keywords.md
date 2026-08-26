# Spec — dynamic hot keywords (replacing the 2018 seed)

**Status:** DESIGN ONLY. Nothing in this document is built. Commissioned 2026-08-24, written
2026-08-26 alongside the brand-facet leak fix. Scope decision at commission: *spec only* — so
this is the contract a later session codes to, not a description of shipped behaviour.

## The problem, measured

Live on trovemo.com 2026-08-26, `GET /srv/search/index` returns:

```json
"defaultKeyword":  {"id":6,"keyword":"Gift Pack Early Access","addTime":[2018,2,1,0,0]},
"hotKeywordList": [{"id":6,"keyword":"Gift Pack Early Access", ...}]
```

One row, seeded in **February 2018** by upstream litemall, is simultaneously the search box's
placeholder and the entire "hot" list. It is not a product we sell, in a 4,135-SKU
home-and-garden catalogue narrowed to two anchor categories. Every shopper sees it.

## What already exists — do NOT rebuild it

`SearchTrendingService` (`application/search/`, nightly 04:45, after the 04:30 promo scorer) is
a complete demand-derived refresh and it is **correct**. It:

- ranks the top-N queries by searches over a window from `litemall_search_stat_daily`;
- marks a matching existing `litemall_keyword` row hot, touching only `is_hot` — curated rows
  keep their text, url and sort;
- adds missing demand queries as new hot rows tagged with the `AUTO_SORT_ORDER = 999`
  sentinel (the table has no origin column, so `sort_order` carries origin; admin rows keep
  the default 100 and therefore honestly sort first);
- un-hots **only** stale sentinel rows — admin curation is never deleted or unhotted;
- **already skips queries that mostly return zero results.** This guard is the important one:
  a hot keyword must never deep-link a shopper into an empty result page.

Config (`litemall.search-stats.*`, all env-backed):
`trending-top: 8`, `trending-window-days: 7`, `trending-min-searches: 3`.

**It is silent for exactly one reason: `trending-min-searches: 3` over a 7-day window is never
met at our traffic.** The machinery is not broken and does not need replacing — it needs a
supply of candidates that does not depend on search volume we do not yet have.

## The honest constraint on "trending from outside"

There is **no free official trending-keyword API**.

- Google Trends' public feed is news-shaped (people, sport, events). Matched against a
  home-and-garden catalogue it yields almost nothing usable.
- Real search-volume data (DataForSEO, Semrush, Ahrefs, Keywords Everywhere) is **paid** and
  requires an account plus a key.

So external trends **cannot be the engine**. They can only ever be an optional *input* that
must pass the same catalogue-fit test as any other candidate. A design that puts them at the
centre would either cost money or quietly serve keywords we cannot fulfil.

## Proposed design

Three parts, deliberately separable. **A and C are self-sufficient** — they need no key, no
vendor and no traffic. B is additive and env-gated.

### A. Catalogue-derived candidates (always on)

A `KeywordCandidateSource` producing candidates from what we actually sell:

- **anchor category names** and their children (the store is narrowed to two L1 roots, so this
  is a small, high-precision set);
- **title n-grams** over on-sale goods, stop-worded, frequency-ranked.

Rank by supply strength, not by guesswork: in-stock count × margin × `listed_num`. `listed_num`
is CJ's own platform-popularity signal, it is **already on `ProductDocument`
(`ProductDocument:75`)** and already multiplies into relevance and powers SuperDeals in
`DiscoveryService` — so this reuses a signal the index carries rather than inventing one.

**Every candidate is executed against our own index and dropped if thin** — the same bar the
existing zero-result guard sets. A candidate that cannot fill a results page is not a hot
keyword, whatever its provenance.

### B. External trends (env-gated adapter, optional)

An `ExternalTrendSource` behind `litemall.search-stats.trends.*` with an API key. Key unset ⇒
the bean is absent and the feature does not exist — the Stripe / Places / Postiz precedent
(unset means *hidden*, never *broken*).

Its candidates pass through the **same** catalogue-fit and non-empty guards as A. An external
term that does not match stock never reaches a shopper.

**External rows need their own sentinel band, not 999.** `sort_order` is the only origin marker
the table has, so reusing 999 would make demand-derived and external rows indistinguishable and
un-un-hottable independently. Suggested: admin `100` (existing) / demand `999` (existing) /
external `998`. Admin curation outranks both, exactly as today.

### C. Default keyword from live hot keywords

`defaultKeyword` currently reads the `is_default` seed row. It should instead be picked from
the live hot set (highest-ranked, with the seed row as last-resort fallback so the field is
never null). This is what actually retires "Gift Pack Early Access" from the search box —
A and B alone would leave it sitting there as the default.

## Acceptance when this is built

- With zero search traffic, `/srv/search/index` serves catalogue-derived hot keywords and a
  default keyword that is **not** the 2018 seed.
- Every served keyword returns a non-thin result page (assert it, don't assume it).
- An admin-curated keyword still outranks and survives every automated pass.
- Trends key unset ⇒ no external candidates, no errors, no behavioural change from A+C alone.
- Un-hotting affects only the matching sentinel band — a demand pass never un-hots an external
  row and vice versa.

## Gotchas for whoever builds this

- `litemall_keyword` has **no origin column**; `sort_order` carries origin by convention. If a
  third origin is added, this convention is at its limit — consider a migration rather than a
  fourth magic number.
- `addTime`/`updateTime` serialise as **arrays** (`[2018,2,1,0,0]`) from goods-management; this
  module also serialises **nulls**. Clients must test for a value, not for key presence.
- The zero-result guard is the feature, not an obstacle. Any new source must pass it.
