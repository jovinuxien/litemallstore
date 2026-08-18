# Season collection — cross-module contract + admin procedure (Wave 27)

**Status:** goods-management half BUILT. gateway-api and gateway-admin halves
NOT started — they code to THIS document, not to anyone's branch.

## 1. Why this exists

"Summer Deals" was never a collection. It was a hardcoded keyword search —
`GoodsListPage.tsx:27` defines `summer: { q: 'summer', source: 'cj' }`, and the
label is hardcoded in `Layout.tsx`, `CategoryDrawer.tsx` and `Home.tsx`. There
was no admin surface at all: nothing to select, order, re-order, schedule or
switch off, and no way to change the label without a rebuild.

Measured live on 2026-08-18, after the Wave-26 narrowing:

- `/summer` returned **39 products** out of a 3,026-product catalogue.
- Matching is relevance-relaxed, so the set is not even reliably on-topic: the
  second result was *"Diving Tube Super Bright Flashlight"*, which does not
  contain the word "summer" anywhere.
- The rest were pool lights, bug zappers, handheld fans, ice-silk cushions and
  cooling blankets — genuinely summer goods, and about to be exactly wrong.

A season is now an ordinary DIY page carrying `category = 'season'`. That one
decision inherits the palette editor, the draft/active lifecycle, the clone
flow, the 64KB config validation and Postiz publishing — all of which already
existed and none of which had to be rebuilt.

## 2. What changed in goods-management

| Change | Where | Note |
|---|---|---|
| `CATEGORY_SEASON = "season"` | `LitemallPage` (litemall-db) | shared module — hand-edited, `mvn install`, restart dependents |
| `'season'` added to the category whitelist | `AdminPageController.CATEGORIES` | without it the page could only be created in SQL |
| `selectActiveByCategory` | `PageMapper.java` + `PageMapper.xml` | hand-edited together |
| `activeByCategory(String)` | `PageService` | mirrors `activeHome()` exactly, including no caching |
| `GET /srv/page/season` | `LitemallPageController` | already anonymous: `/srv/page/**` is on public-paths |
| V63 seed: "Season spotlight" template | migration | draft template — nothing customer-visible on migrate |

**No structural migration was needed for the category itself.** `category` is
`VARCHAR(31)` with no DB-level constraint; V63 only refreshes the now-stale
column comment and seeds the template.

## 3. The contract

### `GET /srv/page/season`

Public, anonymous. Returns the identical `PageView` shape that
`/srv/page/home` and `/srv/page/{id}` already return.

```
200 {"errno": 0, "data": <PageView>}                        -- a season is running
200 {"errno": 642, "errmsg": "no active season page"}       -- none is running
```

Selection rule, in the mapper:

```
category = 'season' AND status = 'active' AND deleted = 0 AND is_template = 0
ORDER BY update_time DESC, id DESC LIMIT 1
```

Three deliberate decisions, each with a reason:

1. **`is_template = 0`.** The seeded template must never become the
   customer-visible season page just because someone activated it.
2. **No single-active invariant.** The home slot enforces one-active in schema
   (generated column + unique index). Season deliberately does not: that would
   need a migration and a demote-then-promote transaction. Two simultaneously
   active season pages are therefore *possible*, and resolve as
   **last-activated-wins** rather than as an arbitrary row. The admin page list
   filters by category+status, so both are visible if it ever happens.
3. **No scheduling columns.** Activation is the switch. A season swap that
   fires unattended at 03:00 is exactly the kind of customer-visible change the
   repo's rules say must stay admin-gated and reversible.

### gateway-api (NOT started)

- Header link, home strip and the `/summer` route become season-driven: title
  from the page `name`, products from its first `goods-list` component, link to
  `/page/<id>` (which already renders).
- **On errno 642 the strip AND the nav link must be ABSENT** — not an empty
  grid, not a dead link. This is the whole degrade rule.
- The old `summer` mode in `GoodsListPage.tsx` and the three hardcoded labels
  come out.

### gateway-admin (NOT started)

- `'season'` in the page-list category filter.
- "New from template" already exists — it just needs to surface the new
  template.

## 4. The admin procedure

This is the "clear procedure" half of the ask. No SQL, no deploy, no rebuild.

1. **Clone the template.** Admin → Pages → filter category `season` → "New from
   template" on *Season spotlight*. You get a draft named "Copy of Season
   spotlight". Rename it to the season ("Autumn spotlight").
2. **Write the copy.** Edit the two rich-text blocks. Keep claims factual —
   nothing about delivery times unless it is true per-SKU (see
   `spec-wave26-eu-sourcing.md`).
3. **Curate the products.** Open the `goods-list` component and switch
   `mode` from `deals` to `byIds`, then paste the product ids you picked.
   **Order is preserved exactly as configured** — the first id is the first
   card. Maximum **24** ids per component; add a second `goods-list` component
   if you want more.
   Pick the ids from Admin → Insight → goods list, where you can sort by margin,
   stock and arrival date before choosing.
4. **Activate it.** The storefront strip appears within the SPA's normal fetch.
   Deactivating it (back to draft) makes the strip and the nav link disappear —
   there is no in-between state.
5. **Promote it.** Admin → Social Publishing (Postiz) → source *DIY page* →
   pick the season page → channels → schedule. This path already exists and
   already handles the page's hero image and link.

**Swapping seasons:** clone a fresh page for the new season and activate it.
The previous season page stays as a draft — it is kept, not deleted, so it can
be reactivated next year. Nothing is destroyed by a swap.

## 5. Why the rail is seeded as `deals`, not `byIds`

The palette validator requires 1–24 **real** goods ids for `mode=byIds`, so a
seeded `byIds` rail could only carry placeholders. Those either render an empty
rail or — worse, if the ids happen to exist in the target environment —
silently advertise products nobody chose. Seeding `deals` means a clone whose
copy has been edited but whose rail has not still renders something sensible,
and curation stays an explicit, documented step. `PageTemplateSeedTest` pins
this: the seeded rail must not be `byIds` and must carry no `goodsIds`.
