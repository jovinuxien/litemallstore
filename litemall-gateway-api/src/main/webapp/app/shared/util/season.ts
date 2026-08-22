import { ApiError, contentApi, IPageComponent, IPageView } from 'app/shared/api';

/**
 * Wave 27 — the season collection, in one place.
 *
 * "Summer Deals" was never a collection: it was `GET /srv/search?q=summer`
 * with its label hardcoded in three components, so the only way to change the
 * season was a rebuild — and on the narrowed catalogue it returned 39 products
 * of 3,026, relevance-relaxed enough that the second hit did not contain the
 * word. A season is now an ordinary DIY page carrying `category='season'`
 * (spec-season-collection.md), which means the NAME and the PRODUCTS are both
 * data an admin owns. Nothing here may hardcode a season's name or its goods.
 *
 * Degrade rule (spec §3, "the whole degrade rule"): with no season running,
 * the nav entry and the strip are ABSENT — never an empty grid, never a link
 * to nothing.
 *
 * Two deliberate departures from `contentAvailability.ts` next door, which
 * gates Brands/Topics/Articles/Group-buys:
 *
 *  1. NO fail-open. Those probes resolve `true` on error, because losing the
 *     store's navigation to a hiccup is worse than one click onto an honest
 *     empty state. Season cannot do that: the link target `/page/<id>` exists
 *     only INSIDE the payload, so a failed fetch leaves nothing to fail open
 *     TO. errno 642 and a transport failure therefore mean the same thing here
 *     — no season to show. They are still distinguished below, because
 *     "nothing is running" and "we could not ask" are different facts.
 *  2. NO sessionStorage. Those probes cache booleans for the whole session.
 *     Caching a season payload would keep a deactivated season on screen until
 *     the tab closed, and activation is the admin's only switch — so this
 *     memoises per page load and re-reads on the next one.
 */

/** goods-management: no active season page. A normal state, not an error. */
export const ERRNO_NO_ACTIVE_SEASON = 642;

/** Shared across every caller in one page load; see the note on caching above. */
const cache: { value?: Promise<IPageView | null> } = {};

/**
 * The running season, or null when none is. Never rejects: callers render an
 * absence, and there is no partial state worth surfacing to a shopper.
 */
export const loadSeason = (): Promise<IPageView | null> => {
  if (!cache.value) {
    cache.value = contentApi
      .pageSeason()
      .then(page => (page && Array.isArray(page.components) ? page : null))
      .catch(err => {
        // 642 is the backend saying "no season is running" — the expected
        // answer most of the year. Anything else is a real failure, and is
        // re-probed on the next page load rather than cached as an absence.
        if (!(err instanceof ApiError && err.errno === ERRNO_NO_ACTIVE_SEASON)) cache.value = undefined;
        return null;
      });
  }
  return cache.value;
};

/**
 * A season worth linking to needs a name: the nav entry IS its name, and an
 * unnamed link is no better for a shopper than a dead one.
 */
export const seasonLabel = (page: IPageView | null): string | null => {
  const name = (page?.name ?? '').trim();
  return name.length > 0 ? name : null;
};

/** The season's own page, which `/page/:id` already renders. */
export const seasonPath = (page: IPageView): string => `/page/${page.id}`;

/**
 * The rail the storefront strip shows: the page's FIRST `goods-list`
 * component. An admin composing a season page controls which products appear
 * and in what order by editing that component (spec §4, step 3).
 */
export const firstGoodsList = (page: IPageView | null): IPageComponent | null =>
  (page?.components ?? []).find(c => c.type === 'goods-list') ?? null;

/** Test seam — drops the memoised season. */
export const __resetSeason = (): void => {
  cache.value = undefined;
};
