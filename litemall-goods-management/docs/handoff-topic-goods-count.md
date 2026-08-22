# Handoff — per-topic goods count on `/srv/topic/list`

**Owner:** litemall-goods-management
**Requested by:** gateway-api (Wave 26 nav honesty, `contentAvailability.ts`)
**Date:** 2026-08-18
**Status:** SHIPPED. No migration, no config, additive to the payload.

## The raise

`contentAvailability.ts:40-49`:

> Topic rows carry no goods count (`/srv/topic/list` returns title/subtitle/price/picUrl only), so
> substance can only be established by reading each topic's detail — capped here so the decision
> costs a handful of small GETs, not one per topic. **Known limit: a substantial topic sorted below
> this cap does not light the nav entry on its own**, though /topics still lists it. A `goodsCount`
> on the topic list row would collapse this to one request — raised for goods-management, not
> worked around here.

The request count was the smaller half. `TOPIC_PROBE_LIMIT = 8` decides the Topics nav entry from
the 8 newest topics, so the probe is not only expensive, it is **incomplete**: a real topic sorted
9th leaves the nav entry dark. A server-side count closes the correctness gap too.

Confirmed at the source: `LitemallTopicService.queryList` selects
`{id, title, subtitle, price, picUrl, readCount}` via `selectByExampleSelective`. The `goods`
column is in `Base_Column_List` but deliberately not in that set, so the raw id array was never in
the list payload either — a client could not have computed this itself.

## What ships

`GET /srv/topic/list` rows gain one field:

```json
{ "id": 1001, "title": "Season picks", "subtitle": "...", "picUrl": "...",
  "readCount": "1k", "price": 12.90, "goodsCount": 6 }
```

`goodsCount` is **how many of the topic's curated goods are still live** — on sale and not
deleted. Everything else in the payload is unchanged, as are `/srv/topic/detail`, `/related` and
the admin topic list.

## Semantics you can rely on

- **It equals what the topic page renders.** `/srv/topic/detail` resolves each curated id through
  `LitemallGoodsService#findByIdVO`, which filters on-sale + not-deleted. The count applies the
  same predicate, so a nav entry driven by it cannot disagree with the page it points at. This is
  an invariant of the code, not a convention — `TopicGoodsCountTest` pins it.
- **`0` is a measurement, not a gap.** A topic with no goods, or whose goods were all off-saled
  (what the Wave-26 narrowing did to all 20 seed topics), reports `0`.
- **`null` means unmeasured.** Verified against the live payload, not assumed: this service
  serializes nulls (`"goods":null`, `"sortOrder":null` are already in every row today), because
  `JacksonConfig` declares a raw `@Bean ObjectMapper` alongside its builder customizer and the raw
  one wins — the same reason `LocalDateTime` serializes as arrays here. So an uncounted row carries
  `"goodsCount": null`, and a row from an older backend carries no key at all. Both are
  "unmeasured": test for a number (`typeof c === 'number'`), not for presence. Unmeasured happens
  in exactly two cases — an older backend, or an unreadable `goods` column (below). Keep treating
  it as "cannot judge ⇒ show", the behaviour you already implement.
- Duplicate ids count once per entry, because `detail` renders one tile per entry.
- Paging is untouched: counts are attached in place, so `total`/`page`/`limit`/`pages` are
  byte-identical to before.

## One caveat worth knowing

`JsonIntegerArrayTypeHandler` throws on a malformed `goods` column, and it fails the whole result
set rather than one row. Reading that column for the count therefore introduced a failure mode the
list did not have. It is caught: the counts go unmeasured (`null`) and the list is served normally,
rather than one corrupt row 500-ing an anonymous endpoint.

Note `/srv/topic/detail` has always had this exposure and still does — it reads `goods` directly.
Left alone deliberately; it is a pre-existing behaviour, not something this change should quietly
alter.

## What gateway-api can now do

Replace the per-topic detail probe with the single `/srv/topic/list` call, dropping
`TOPIC_PROBE_LIMIT` and its documented blind spot: read `goodsCount` per row, treat a row with a
numeric `0` as empty, and an absent field as unjudgeable (show). `/topics` can list only rows with
`goodsCount > 0` the same way `/brands` already filters on `goodsCount` — the brand precedent is
`handoff-brand-goods-count.md`.

## Where it lives

| Piece | File |
|---|---|
| `goodsCount` (computed, not a column) | `LitemallTopic` |
| `attachGoodsCounts` — 2 queries per page, in place | `LitemallTopicService` |
| live-subset query | `LitemallCjLinkageMapper#selectOnSaleGoodsIds` (+ XML) |
| wiring | `TopicQueryService#list` |
| tests | `TopicGoodsCountTest` (real MySQL), `TopicGoodsCountDegradeTest`, `TopicQueryServiceTest` |

`litemall-db` is shared and hand-maintained: entity + mapper XML were hand-edited together, and
every dependent service needs a rebuild to pick the field up.
