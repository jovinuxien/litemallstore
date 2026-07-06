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
type Route = { q?: string; category_ids?: string; brand?: string; price?: string; sort?: string; page?: string };

export const searchRouting = {
  router: historyRouter<Route>(),
  stateMapping: {
    stateToRoute(uiState: UiState): Route {
      const s = (uiState[PRIMARY_INDEX] ?? {}) as any;
      const rl = s.refinementList ?? {};
      const route: Route = {};
      if (s.query) route.q = s.query;
      if (rl.category_ids?.length) route.category_ids = rl.category_ids.join(',');
      if (rl.brand?.length) route.brand = rl.brand.join(',');
      if (s.range?.price) route.price = s.range.price;
      if (s.sortBy && s.sortBy !== PRIMARY_INDEX) route.sort = String(s.sortBy).split('/sort/')[1];
      if (s.page && s.page > 1) route.page = String(s.page);
      return route;
    },
    routeToState(route: Route = {}): UiState {
      const refinementList: Record<string, string[]> = {};
      if (route.category_ids) refinementList.category_ids = route.category_ids.split(',');
      if (route.brand) refinementList.brand = route.brand.split(',');
      return {
        [PRIMARY_INDEX]: {
          query: route.q,
          page: route.page ? Number(route.page) : undefined,
          refinementList: Object.keys(refinementList).length ? refinementList : undefined,
          range: route.price ? { price: route.price } : undefined,
          sortBy: route.sort ? sortIndex(route.sort) : undefined,
        },
      } as UiState;
    },
  },
};

export default searchRouting;
