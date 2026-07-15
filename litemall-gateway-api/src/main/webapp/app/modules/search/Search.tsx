import React, { useEffect, useMemo, useState } from 'react';
import {
  ClearRefinements,
  Configure,
  CurrentRefinements,
  Hits,
  InstantSearch,
  Pagination,
  RangeInput,
  RefinementList,
  SortBy,
  Stats,
} from 'react-instantsearch';
import { Link, useLocation, useParams } from 'react-router-dom';

import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getCatalogAllData, getCatalogIndexData } from 'app/modules/Category/categorySlice';
import { CategoryData } from 'app/shared/model/category/category.models';
import 'app/components/userComponents/card/product-card.scss';

import CategoryTree from './instantsearch/CategoryTree';
import CatalogTreeNav from './instantsearch/CatalogTreeNav';
import ProductHit from './instantsearch/ProductHit';
import { litemallSearchClient, PRIMARY_INDEX, sortIndex } from './instantsearch/litemallSearchClient';
import { searchRouting } from './instantsearch/searchRouting';
import './instantsearch/search.scss';

/**
 * Canonical faceted search / browse page, powered by react-instantsearch over
 * goods-management's `GET /srv/search` (gateway → goods-management → OCS) via
 * `litemallSearchClient`. It is NOT wired to Elasticsearch directly: every query
 * is relayed by baseAxios so the customer JWT travels exactly as for the rest of
 * the SPA, and OCS stays the search engine.
 *
 * Entry points all land here:
 *   - header search box   -> /search?q=<term>
 *   - category flyout      -> /search?category_ids=<id>
 *   - home / cart tiles    -> /category/:id   (path param, seeded below)
 * `searchRouting` keeps clean storefront URLs and absorbs the query-string
 * deep-links; the `/category/:id` path param is seeded via initialUiState.
 */

// Sort options map to the virtual sort-index names the search client understands.
const SORT_ITEMS = [
  { label: 'Relevance', value: PRIMARY_INDEX },
  { label: 'Price: low to high', value: sortIndex('price') },
  { label: 'Price: high to low', value: sortIndex('-price') },
];

// Facets rendered explicitly (with custom labels / a range control) above, plus
// `category_names` which is the same data as the explicit `category_ids` facet
// (by name instead of id) — showing both would duplicate the Category filter.
const KNOWN_FACETS = new Set(['category_ids', 'category_names', 'brand', 'price']);

// "attr_material" / "screen_size" -> "Material" / "Screen Size" for facet headers.
const humanizeFacet = (field: string): string =>
  field
    .replace(/^attr[_-]/i, '')
    .replace(/_(ids?|names?)$/i, '')
    .replace(/[_-]+/g, ' ')
    .replace(/\b\w/g, c => c.toUpperCase())
    .trim() || field;

type FacetGroupMeta = { field: string; type: string };

/**
 * Renders one refinement per facet group the backend returns BEYOND the three
 * explicit ones (category/brand/price). goods-management's SearchService emits a
 * dynamic `filters[]` — it forwards every param to OCS and returns whatever
 * facets the index is configured with (attribute & variant facets included), so
 * the sidebar must mirror that set instead of a hardcoded list.
 *
 * The field set is discovered with a direct `/srv/search` probe rather than from
 * InstantSearch results: InstantSearch drops response facets that no mounted
 * widget requested, so reading the field list off `useInstantSearch()` is
 * chicken-and-egg. Once we mount a widget for a discovered field, InstantSearch
 * requests it and the adapter's `facets`/`facets_stats` populate it normally.
 * Interval groups (`price`, `variant_price`) become a RangeInput; the rest a
 * RefinementList.
 */
const DynamicExtraFacets: React.FC = () => {
  const [groups, setGroups] = useState<FacetGroupMeta[]>([]);

  useEffect(() => {
    let cancelled = false;
    baseAxios
      .get(`${BASE_URL_CONTEXT}/search?q=&page=1&size=1`)
      .then(res => {
        const d = res.data?.data ?? res.data ?? {};
        const raw: any[] = Array.isArray(d.filters) ? d.filters : Array.isArray(d.facetGroups) ? d.facetGroups : [];
        const metas = raw.map(g => ({ field: g.field ?? g.fieldName ?? '', type: g.type ?? 'term' })).filter(g => g.field);
        if (!cancelled) setGroups(metas);
      })
      .catch(() => {
        /* leave the dynamic section empty; the explicit facets still render */
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const extra = groups.filter(g => !KNOWN_FACETS.has(g.field));
  if (!extra.length) return null;
  return (
    <>
      {extra.map(g => (
        <section className="lm-isearch__facet" key={g.field}>
          <h3>{humanizeFacet(g.field)}</h3>
          {g.type === 'interval' ? <RangeInput attribute={g.field} /> : <RefinementList attribute={g.field} limit={8} showMore />}
        </section>
      ))}
    </>
  );
};

const SearchView: React.FC = () => {
  const dispatch = useAppDispatch();
  const params = useParams<{ id?: string }>();
  const location = useLocation();
  // The header search bar is THE search box (there is no SearchBox widget on
  // this page). A header submit navigates to /search?q=<term>; keying the
  // InstantSearch mount on that q makes the navigation start a fresh search
  // (routing re-reads the URL, old refinements cleared — Amazon behavior).
  // Refinement-driven URL rewrites go through InstantSearch's own history
  // router, which react-router doesn't observe, so this key stays stable
  // while the user filters.
  const headerQuery = new URLSearchParams(location.search).get('q') ?? '';

  // Category facet values are ids; build an id -> name map from the catalog data
  // already fetched for the home/menu so the refinement list shows readable
  // names instead of numeric ids. The /catalog payload serialises each category
  // as `{ categoryId: { id }, categoryName }` (NOT the flat `{ id, name }` the
  // CategoryData type declares), so read both shapes defensively — the flat-key
  // read alone left the map empty and the facet fell back to ids. Unknown ids
  // still fall back to the raw id.
  const categoryState = useAppSelector(state => state.category.data);
  const categoryNames = useMemo(() => {
    const map = new Map<string, string>();
    const add = (list?: CategoryData[]) =>
      (list ?? []).forEach(c => {
        const cat = c as any;
        const id = cat?.categoryId?.id ?? cat?.id;
        const name = cat?.categoryName ?? cat?.name;
        if (id != null && name) map.set(String(id), name);
      });
    add(categoryState.dataCategoryIndex?.categoryList);
    add(categoryState.dataCatalogAll?.categoryList);
    Object.values(categoryState.dataCatalogAll?.allList ?? {}).forEach(add);
    return map;
  }, [categoryState]);

  useEffect(() => {
    // Populate the category name map (no-ops if the home page already loaded it).
    dispatch(getCatalogIndexData());
    dispatch(getCatalogAllData());
  }, [dispatch]);

  // /category/:id scope is applied via <Configure facetFilters> below (not a
  // RefinementList): on a category route the flat category facet is replaced by
  // the subcategory tree, so there's no widget to carry an initialUiState
  // refinement — Configure scopes the result set straight through the adapter.
  // Query-string deep-links (?q=, ?category_ids=, …) are handled by searchRouting.

  return (
    <div className="lm-isearch">
      <InstantSearch
        // Remount per category route so initialUiState re-seeds the scope when
        // drilling between categories (otherwise the refinement, seeded once at
        // mount, would go stale on /category/:id → /category/:childId), and per
        // header-submitted query (see headerQuery above).
        key={`${params.id ?? 'search'}:${headerQuery}`}
        searchClient={litemallSearchClient}
        indexName={PRIMARY_INDEX}
        routing={searchRouting}
        future={{ preserveSharedStateOnUnmount: true }}
      >
        <Configure hitsPerPage={12} {...(params.id ? { facetFilters: [`category_ids:${params.id}`] } : {})} />

        <div className="lm-isearch__body">
          {/* ── Filter rail ─────────────────────────────────────────────── */}
          <aside className="lm-isearch__rail">
            <div className="lm-isearch__rail-head">
              <h2>Filters</h2>
              <ClearRefinements translations={{ resetButtonText: 'Clear all' }} />
            </div>

            {params.id ? (
              // On a /category/:id route, show the real subcategory tree + breadcrumb
              // (with counts) instead of the flat category facet, which OCS collapses
              // to the selected id alone once a category filter is active.
              <CategoryTree categoryId={params.id} />
            ) : (
              <>
                {/* Browse tree: top categories (server-ordered by goods count) expanding to
                    their subcategories; navigates to /category/:id. The flat facet below stays
                    for query-scoped multi-select filtering and ?category_ids= deep links. */}
                <CatalogTreeNav />
                <section className="lm-isearch__facet">
                  <h3>Filter by category</h3>
                  <RefinementList
                    attribute="category_ids"
                    limit={8}
                    showMore
                    transformItems={items => items.map(it => ({ ...it, label: categoryNames.get(it.label) ?? it.label }))}
                  />
                </section>
              </>
            )}

            <section className="lm-isearch__facet">
              <h3>Brand</h3>
              <RefinementList attribute="brand" limit={8} showMore />
            </section>

            <section className="lm-isearch__facet">
              <h3>Price</h3>
              <RangeInput attribute="price" />
            </section>

            {/* Every other facet group the backend returns (attributes, variant
                fields, …) — rendered dynamically so new OCS facets need no SPA change. */}
            <DynamicExtraFacets />
          </aside>

          {/* ── Results ─────────────────────────────────────────────────── */}
          <section className="lm-isearch__results">
            <div className="lm-isearch__results-head">
              <Stats />
              <label className="lm-isearch__sort">
                Sort by
                <SortBy items={SORT_ITEMS} />
              </label>
            </div>

            <CurrentRefinements
              transformItems={items =>
                items.map(item => ({
                  ...item,
                  label: item.attribute === 'category_ids' ? 'Category' : item.label,
                  refinements: item.refinements.map(r => ({
                    ...r,
                    label: item.attribute === 'category_ids' ? categoryNames.get(String(r.value)) ?? r.label : r.label,
                  })),
                }))
              }
            />

            <Hits hitComponent={ProductHit} classNames={{ list: 'lm-isearch__grid' }} />

            <Pagination className="lm-isearch__pager" padding={2} />
          </section>
        </div>
      </InstantSearch>

      <div className="lm-isearch__foot">
        <Link to="/">← Continue shopping</Link>
      </div>
    </div>
  );
};

export default SearchView;
