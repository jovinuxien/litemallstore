import historyRouter from 'instantsearch.js/es/lib/routers/history';
import type { UiState } from 'instantsearch.js';

import { PRIMARY_INDEX, sortIndex } from './litemallSearchClient';

/**
 * InstantSearch routing for the canonical /search page. Keeps storefront-clean
 * query strings (`?q=&category_ids=&brand=&price=&sort=&page=`) instead of the
 * verbose default `litemall_index[...]` encoding, and — because routeToState
 * reads those same keys — natively absorbs the deep-links the rest of the SPA
 * emits: the header search box (`/search?q=`), the category flyout
 * (`/search?category_ids=<id>`), and "shop more" links. The `/category/:id`
 * PATH param is seeded separately via initialUiState in the page component.
 */
type Route = Record<string, string | undefined>;

// Keys with dedicated (non-facet) meaning in the route.
const RESERVED_KEYS = new Set(['q', 'sort', 'page']);
// An InstantSearch range refinement serialises as "min:max" (either side open).
const RANGE_SHAPE = /^-?\d*(?:\.\d+)?:-?\d*(?:\.\d+)?$/;

export const searchRouting = {
  router: historyRouter<Route>(),
  stateMapping: {
    stateToRoute(uiState: UiState): Route {
      const s = (uiState[PRIMARY_INDEX] ?? {}) as any;
      const route: Route = {};
      if (s.query) route.q = s.query;
      // EVERY refinement-list facet (category_ids, brand, and the dynamic
      // attribute facets like attr_material) gets its own query key; ranges
      // (price, weight, …) keep their "min:max" value. Hardcoding just
      // category_ids/brand/price silently dropped the dynamic facets from the
      // URL, so back/refresh restored a different result set than on screen.
      Object.entries(s.refinementList ?? {}).forEach(([attr, values]) => {
        if (!RESERVED_KEYS.has(attr) && Array.isArray(values) && values.length) route[attr] = values.join(',');
      });
      Object.entries(s.range ?? {}).forEach(([attr, value]) => {
        if (!RESERVED_KEYS.has(attr) && value) route[attr] = String(value);
      });
      if (s.sortBy && s.sortBy !== PRIMARY_INDEX) route.sort = String(s.sortBy).split('/sort/')[1];
      if (s.page && s.page > 1) route.page = String(s.page);
      return route;
    },
    routeToState(route: Route = {}): UiState {
      const refinementList: Record<string, string[]> = {};
      const range: Record<string, string> = {};
      Object.entries(route).forEach(([key, value]) => {
        if (RESERVED_KEYS.has(key) || !value) return;
        if (RANGE_SHAPE.test(value)) range[key] = value;
        else refinementList[key] = value.split(',');
      });
      // A foreign query param (e.g. ?utm_source=) lands in refinementList here,
      // but InstantSearch drops uiState slices no mounted widget consumes, so
      // it never reaches the backend.
      return {
        [PRIMARY_INDEX]: {
          query: route.q,
          page: route.page ? Number(route.page) : undefined,
          refinementList: Object.keys(refinementList).length ? refinementList : undefined,
          range: Object.keys(range).length ? range : undefined,
          sortBy: route.sort ? sortIndex(route.sort) : undefined,
        },
      } as UiState;
    },
  },
};

export default searchRouting;
