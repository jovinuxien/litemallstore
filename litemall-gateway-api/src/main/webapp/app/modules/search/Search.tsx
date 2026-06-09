import React, { useEffect, useMemo } from 'react';
import {
  ClearRefinements,
  Configure,
  CurrentRefinements,
  Hits,
  InstantSearch,
  Pagination,
  RangeInput,
  RefinementList,
  SearchBox,
  SortBy,
  Stats,
} from 'react-instantsearch';
import { Link, useParams } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getCatalogAllData, getCatalogIndexData } from 'app/modules/Category/categorySlice';
import { CategoryData } from 'app/shared/model/category/category.models';
import 'app/components/userComponents/card/product-card.scss';

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

const SearchView: React.FC = () => {
  const dispatch = useAppDispatch();
  const params = useParams<{ id?: string }>();

  // Category facet values are ids; build an id -> name map from the catalog data
  // already fetched for the home/menu so the refinement list shows readable
  // names. Falls back to the raw id when a name isn't known (category-name
  // facets remain a goods-management follow-up).
  const categoryState = useAppSelector(state => state.category.data);
  const categoryNames = useMemo(() => {
    const map = new Map<string, string>();
    const add = (list?: CategoryData[]) => (list ?? []).forEach(c => c?.id != null && map.set(String(c.id), c.name));
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

  // /category/:id deep-link: seed the category refinement. Query-string
  // deep-links (?q=, ?category_ids=, …) are handled by searchRouting.
  const initialUiState = params.id
    ? { [PRIMARY_INDEX]: { refinementList: { category_ids: [params.id] } } }
    : undefined;

  return (
    <div className="lm-isearch">
      <InstantSearch
        searchClient={litemallSearchClient}
        indexName={PRIMARY_INDEX}
        initialUiState={initialUiState}
        routing={searchRouting}
        future={{ preserveSharedStateOnUnmount: true }}
      >
        <Configure hitsPerPage={12} />

        <div className="lm-isearch__bar">
          <SearchBox placeholder="Search products…" className="lm-isearch__box" />
        </div>

        <div className="lm-isearch__body">
          {/* ── Filter rail ─────────────────────────────────────────────── */}
          <aside className="lm-isearch__rail">
            <div className="lm-isearch__rail-head">
              <h2>Filters</h2>
              <ClearRefinements translations={{ resetButtonText: 'Clear all' }} />
            </div>

            <section className="lm-isearch__facet">
              <h3>Category</h3>
              <RefinementList
                attribute="category_ids"
                limit={8}
                showMore
                transformItems={items => items.map(it => ({ ...it, label: categoryNames.get(it.label) ?? it.label }))}
              />
            </section>

            <section className="lm-isearch__facet">
              <h3>Brand</h3>
              <RefinementList attribute="brand" limit={8} showMore />
            </section>

            <section className="lm-isearch__facet">
              <h3>Price</h3>
              <RangeInput attribute="price" />
            </section>
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
