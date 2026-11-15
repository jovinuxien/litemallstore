import React, { useCallback, useEffect, useMemo, useState } from 'react';
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
  ToggleRefinement,
  useInstantSearch,
  useSearchBox,
} from 'react-instantsearch';
import { Link, useLocation, useParams } from 'react-router-dom';

import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { useAppSelector } from 'app/config/store';
import { i18n, type TFunction, useTranslation } from 'app/i18n';
import { fetchSearchIndex } from 'app/modules/search/searchIndexApi';
import { CategoryData } from 'app/shared/model/category/category.models';
import 'app/components/userComponents/card/product-card.scss';

import CategoryTree from './instantsearch/CategoryTree';
import { emptyStateCopy, hasRefinements } from './instantsearch/emptyStateCopy';
import { FacetGroupMeta, FacetProbeStatus, readFacetGroups, shouldShowFacet } from './instantsearch/facetVisibility';
import CatalogTreeNav from './instantsearch/CatalogTreeNav';
import ProductHit from './instantsearch/ProductHit';
import SearchUnavailableState from './instantsearch/SearchUnavailableState';
import { createSearchClient, PRIMARY_INDEX, sortIndex } from './instantsearch/litemallSearchClient';
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

// Fallback sort options, used only until/unless the backend returns its own
// `sortOptions` (read off every /srv/search response into the search slice by
// litemallSearchClient). Values map to the virtual sort-index names the search
// client understands.
const fallbackSortItems = (t: TFunction) => [
  { label: t('search:sort.relevance'), value: PRIMARY_INDEX },
  { label: t('search:sort.fields.price.asc'), value: sortIndex('price') },
  { label: t('search:sort.fields.price.desc'), value: sortIndex('-price') },
];

// A server sort value that means "no explicit sort" maps to the primary index.
const RELEVANCE_VALUES = /^-?(_score|relevance|default)$/i;

// Today the backend labels its sort options with the raw searcher strings
// ("price.asc", "review_count.desc"). Prettify ONLY that raw shape — a label
// that doesn't match it is assumed human-authored and passes through untouched
// (verbatim, in whatever language the server wrote it — the same rule
// describeError applies to unknown errnos), so a later backend improvement wins
// automatically. Known fields resolve to `search:sort.fields.<field>.<dir>`.
const RAW_SORT_LABEL = /^([a-z0-9_]+)\.(asc|desc)$/i;
const SORT_FIELDS = new Set([
  'price',
  'variant_price',
  'rating',
  'review_count',
  'discount_pct',
  'discount_price',
  'listed_num',
  'title',
  'created_epoch',
  'deal_end_epoch',
]);

export const prettySortLabel = (label: string, t: TFunction): string => {
  const m = RAW_SORT_LABEL.exec(label.trim());
  if (!m) return label;
  const [, field, rawDir] = m;
  const dir = rawDir.toLowerCase() === 'asc' ? 'asc' : 'desc';
  const key = field.toLowerCase();
  if (SORT_FIELDS.has(key)) return t(`search:sort.fields.${key}.${dir}`);
  return t('search:sort.generic', { field: humanizeFacet(field), direction: t(`search:sort.${dir === 'asc' ? 'ascending' : 'descending'}`) });
};

// Facets rendered explicitly (with custom labels / a range control) above, plus
// `category_names` which is the same data as the explicit `category_ids` facet
// (by name instead of id) — showing both would duplicate the Category filter.
// `coupon_flag` / `groupon_flag` have their own toggles in the Offers section,
// `eu_flag` its own toggle under Delivery.
const KNOWN_FACETS = new Set([
  'category_ids',
  'category_names',
  'brand',
  'price',
  'coupon_flag',
  'groupon_flag',
  'eu_flag',
]);

// "attr_material" / "screen_size" -> "Material" / "Screen Size" for facet headers.
const humanizeFacet = (field: string): string =>
  field
    .replace(/^attr[_-]/i, '')
    .replace(/_(ids?|names?)$/i, '')
    .replace(/[_-]+/g, ' ')
    .replace(/\b\w/g, c => c.toUpperCase())
    .trim() || field;

// Facet headings: a core field with a catalogued name (`search:rail.facetFields.*`)
// renders that; every other dynamic facet (attribute / variant fields the index is
// configured with) keeps the humanised field name — those come from index config,
// and cataloguing them one by one would be a maintenance trap, not a translation.
const facetHeading = (field: string, t: TFunction): string =>
  i18n.exists(`search:rail.facetFields.${field}`) ? t(`search:rail.facetFields.${field}`) : humanizeFacet(field);

/**
 * Localised widget chrome ("Show more", "to", pager aria-labels, …). MEMOISED on
 * `t`: react-instantsearch diffs widget props with dequal and functions compare
 * by reference, so an inline `translations={{ … }}` would remount the widget —
 * and schedule a fresh search — on every render (see the useCallback note in
 * SearchView). `t` changes identity only on a language switch, which is the one
 * time a remount is wanted.
 */
const useWidgetTranslations = (t: TFunction) =>
  useMemo(
    () => ({
      refinementList: {
        showMoreButtonText: ({ isShowingMore }: { isShowingMore: boolean }) => (isShowingMore ? t('search:widgets.showLess') : t('search:widgets.showMore')),
        noResultsText: t('search:widgets.noResults'),
      },
      rangeInput: { separatorElementText: t('search:widgets.rangeSeparator'), submitButtonText: t('search:widgets.go') },
      pagination: {
        firstPageItemAriaLabel: t('search:widgets.firstPage'),
        previousPageItemAriaLabel: t('search:widgets.previousPage'),
        nextPageItemAriaLabel: t('search:widgets.nextPage'),
        lastPageItemAriaLabel: t('search:widgets.lastPage'),
        pageItemAriaLabel: ({ currentPage }: { currentPage: number }) => t('search:widgets.page', { page: currentPage }),
      },
      stats: { rootElementText: ({ nbHits }: { nbHits: number }) => t('search:widgets.stats', { count: nbHits }) },
      clearRefinements: { resetButtonText: t('search:rail.clearAll') },
    }),
    [t]
  );

/**
 * ONE probe of the backend's facet groups, shared by the explicit Brand section
 * and the dynamic ones so they cannot disagree about what the index holds.
 *
 * Scoped on /category/:id so only groups that actually occur in this category
 * are offered (no dead "Material" list on a furniture category).
 */
const useFacetGroups = (categoryId?: string): { groups: FacetGroupMeta[]; status: FacetProbeStatus } => {
  const [groups, setGroups] = useState<FacetGroupMeta[]>([]);
  const [status, setStatus] = useState<FacetProbeStatus>('pending');

  useEffect(() => {
    let cancelled = false;
    setStatus('pending');
    baseAxios
      .get(`${BASE_URL_CONTEXT}/search?q=&page=1&size=1${categoryId ? `&category_ids=${encodeURIComponent(categoryId)}` : ''}`)
      .then(res => {
        if (cancelled) return;
        setGroups(readFacetGroups(res.data?.data ?? res.data ?? {}));
        setStatus('ready');
      })
      .catch(() => {
        // Fail OPEN: an unreachable probe leaves the rail as it was rather than
        // stripping filters the index may well still have.
        if (!cancelled) setStatus('failed');
      });
    return () => {
      cancelled = true;
    };
  }, [categoryId]);

  return { groups, status };
};

/** True when the user has a refinement on `attribute` right now. */
const useIsRefined = (attribute: string): boolean => {
  const { indexUiState } = useInstantSearch();
  const list = (indexUiState.refinementList ?? {})[attribute];
  return Array.isArray(list) && list.length > 0;
};

/**
 * The Brand section, rendered only when the brand facet has values (or the
 * probe failed, or a brand refinement is active). See facetVisibility.ts —
 * goods-management now excludes uncurated supplier names from the facet, so
 * this heading is the one most likely to end up standing over nothing.
 */
const BrandFacet: React.FC<{ categoryId?: string }> = ({ categoryId }) => {
  const { t } = useTranslation('search');
  const widget = useWidgetTranslations(t);
  const { groups, status } = useFacetGroups(categoryId);
  const refined = useIsRefined('brand');
  if (!shouldShowFacet({ status, groups, field: 'brand', refined })) return null;
  return (
    <section className="lm-isearch__facet">
      <h3>{t('rail.brand')}</h3>
      <RefinementList attribute="brand" limit={8} showMore translations={widget.refinementList} />
    </section>
  );
};

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
const DynamicExtraFacets: React.FC<{ categoryId?: string }> = ({ categoryId }) => {
  const { t } = useTranslation('search');
  const widget = useWidgetTranslations(t);
  const { groups, status } = useFacetGroups(categoryId);

  // A group with no buckets used to render a heading over an empty list: the
  // old filter checked only that the backend NAMED the field. `status` is
  // irrelevant here — a failed probe yields no groups at all, so there is
  // nothing to render either way.
  const extra = groups.filter(g => !KNOWN_FACETS.has(g.field) && shouldShowFacet({ status, groups, field: g.field }));
  if (!extra.length) return null;
  return (
    <>
      {extra.map(g => (
        <section className="lm-isearch__facet" key={g.field}>
          <h3>{facetHeading(g.field, t)}</h3>
          {g.type === 'interval' ? (
            <RangeInput attribute={g.field} translations={widget.rangeInput} />
          ) : (
            <RefinementList attribute={g.field} limit={8} showMore translations={widget.refinementList} />
          )}
        </section>
      ))}
    </>
  );
};

/**
 * Invisible query consumer. The page deliberately mounts no <SearchBox> (the
 * header bar is THE search box), but InstantSearch only forwards `uiState`
 * slices that some mounted widget consumes — with no search-box widget the
 * `query` seeded by searchRouting/initialUiState was silently DROPPED and every
 * request went out with `q=` (all goods, no matter what the user searched).
 * Mounting the connector — without rendering anything — makes `query` stick.
 */
const VirtualSearchBox: React.FC = () => {
  useSearchBox();
  return null;
};

/**
 * Swaps the results grid for `fallback` once a search has actually settled at
 * zero hits. `__isArtificial` marks InstantSearch's synthetic pre-first-response
 * results, so the empty state never flashes while the first request is in
 * flight.
 */
const NoResultsBoundary: React.FC<{ fallback: React.ReactNode; children: React.ReactNode }> = ({ fallback, children }) => {
  const { results } = useInstantSearch();
  if (results && !(results as any).__isArtificial && results.nbHits === 0) return <>{fallback}</>;
  return <>{children}</>;
};

/**
 * Intentional zero-results state: name the query that found nothing, then offer
 * ways onward — trending keywords (GET /srv/search/index) and the top catalog
 * categories (already in redux for the header drawer / home page).
 */
const SearchEmptyState: React.FC<{ categoryId?: string }> = ({ categoryId }) => {
  const { t } = useTranslation('search');
  const { results, indexUiState } = useInstantSearch();
  const query = (results?.query ?? '').trim();
  // Why the page is empty decides what it may say — a retired department, a
  // filter that matched nothing, or a term that found nothing are three
  // different statements (see instantsearch/emptyStateCopy.ts).
  const copy = emptyStateCopy({ categoryId, query, refined: hasRefinements(indexUiState as Record<string, unknown>) });
  const [trending, setTrending] = useState<string[]>([]);

  useEffect(() => {
    let cancelled = false;
    fetchSearchIndex()
      .then(d => {
        if (!cancelled) setTrending(d.hotKeywords);
      })
      .catch(() => {
        /* the empty state still renders without trending */
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Top (L1) categories from /srv/catalog/all — the backend orders that list by
  // on-sale goods count (same source as the browse tree), so the first entries
  // genuinely are the popular ones; the index list is unordered seed data. Same
  // defensive dual-shape read as the category-name map below
  // (`{categoryId:{id}, categoryName}` vs the flat `{id, name}` the type declares).
  const catalogAll = useAppSelector(state => state.category.data.dataCatalogAll?.categoryList);
  const catalogIndex = useAppSelector(state => state.category.data.dataCategoryIndex?.categoryList);
  const topCategories = catalogAll?.length ? catalogAll : catalogIndex ?? [];
  const categories = topCategories
    .map(c => {
      const cat = c as any;
      return { id: cat?.categoryId?.id ?? cat?.id, name: cat?.categoryName ?? cat?.name };
    })
    .filter(c => c.id != null && c.name)
    .slice(0, 8);

  return (
    <div className="lm-isearch__empty">
      <h2>{copy.title}</h2>
      <p>{copy.body}</p>
      {trending.length > 0 && (
        <section>
          <h3>{t('empty.trending')}</h3>
          <div className="lm-isearch__empty-chips">
            {trending.slice(0, 10).map(k => (
              <Link key={k} to={`/search?q=${encodeURIComponent(k)}`} className="lm-isearch__empty-chip">
                {k}
              </Link>
            ))}
          </div>
        </section>
      )}
      {categories.length > 0 && (
        <section>
          <h3>{t('empty.popularCategories')}</h3>
          <div className="lm-isearch__empty-chips">
            {categories.map(c => (
              <Link key={c.id} to={`/category/${c.id}`} className="lm-isearch__empty-chip lm-isearch__empty-chip--cat">
                {c.name}
              </Link>
            ))}
          </div>
        </section>
      )}
    </div>
  );
};

const SearchView: React.FC = () => {
  const { t } = useTranslation('search');
  const widget = useWidgetTranslations(t);
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

  // Extra per-response fields the Algolia shape can't carry — published by
  // litemallSearchClient into the search slice (sort options, relaxed-match).
  const meta = useAppSelector(state => state.search.meta);

  // Server-driven sort options: map each `{label, value: 'field'|'-field'}`
  // onto the client's virtual sort indices; relevance-ish/empty values mean the
  // primary index. Fall back to the hardcoded list until the field arrives.
  const sortItems = useMemo(() => {
    if (!meta.sortOptions.length) return fallbackSortItems(t);
    const items = meta.sortOptions
      .filter(o => o.label)
      .map(o => ({
        label: prettySortLabel(o.label, t),
        value: o.value && !RELEVANCE_VALUES.test(o.value) ? sortIndex(o.value) : PRIMARY_INDEX,
      }));
    if (!items.some(it => it.value === PRIMARY_INDEX)) items.unshift({ label: t('sort.relevance'), value: PRIMARY_INDEX });
    const seen = new Set<string>();
    return items.filter(it => (seen.has(it.value) ? false : (seen.add(it.value), true)));
  }, [meta.sortOptions, t]);

  // "Did you mean / similar results" banner: OCS reported it relaxed the query
  // (typo/fuzzy fallback) for the CURRENT header query and still found hits.
  // (At zero hits the empty state takes over instead.)
  const relaxedQuery = meta.query.trim();
  const showRelaxedBanner = meta.relaxed && meta.total > 0 && relaxedQuery.length > 0 && relaxedQuery === headerQuery.trim();

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

  // (The catalog thunks that populate the name map are dispatched by Layout on
  // mount — re-dispatching them here just churned state.category.data and made
  // every widget with a categoryNames-derived prop remount mid-session.)

  // /category/:id scope is pinned INSIDE the search client (createSearchClient):
  // every request — main query and facet-count queries alike — carries
  // category_ids, so no widget refinement can drop the scope. (The previous
  // <Configure facetFilters> injection was overwritten by the helper's merge as
  // soon as any facet was refined, flipping the page to whole-catalog results.)
  // Query-string deep-links (?q=, ?category_ids=, …) are handled by searchRouting.
  const searchClient = useMemo(() => createSearchClient(params.id), [params.id]);

  // Stable identities for widget props: react-instantsearch diffs widget props
  // with dequal (functions compare by REFERENCE) and remove+re-adds the widget
  // when anything "changed" — each re-add schedules a fresh search. Inline
  // arrows here made every render remount CurrentRefinements/RefinementList,
  // and each search's meta dispatch re-rendered the page: a self-sustaining
  // refetch loop. useCallback pins them between category-data updates.
  const transformCategoryFacetItems = useCallback(
    (items: any[]) => items.map(it => ({ ...it, label: categoryNames.get(it.label) ?? it.label })),
    [categoryNames]
  );
  const transformCurrentRefinements = useCallback(
    (items: any[]) =>
      items.map(item => ({
        ...item,
        label:
          item.attribute === 'category_ids'
            ? t('rail.category')
            : item.attribute === 'coupon_flag' || item.attribute === 'groupon_flag'
              ? t('rail.offers')
              : item.attribute === 'eu_flag'
                ? t('rail.delivery')
                : item.label,
        refinements: item.refinements.map((r: any) => ({
          ...r,
          label:
            item.attribute === 'category_ids'
              ? categoryNames.get(String(r.value)) ?? r.label
              : item.attribute === 'coupon_flag'
                ? t('rail.hasCoupon')
                : item.attribute === 'groupon_flag'
                  ? t('rail.groupBuy')
                  : item.attribute === 'eu_flag'
                    ? t('rail.inEuStock')
                    : r.label,
        })),
      })),
    [categoryNames, t]
  );

  return (
    <div className="lm-isearch">
      <InstantSearch
        // Remount per category route so initialUiState re-seeds the scope when
        // drilling between categories (otherwise the refinement, seeded once at
        // mount, would go stale on /category/:id → /category/:childId), and per
        // header-submitted query (see headerQuery above).
        key={`${params.id ?? 'search'}:${headerQuery}`}
        searchClient={searchClient}
        indexName={PRIMARY_INDEX}
        routing={searchRouting}
        future={{ preserveSharedStateOnUnmount: true }}
      >
        <Configure hitsPerPage={12} />
        <VirtualSearchBox />

        <div className="lm-isearch__body">
          {/* ── Filter rail ─────────────────────────────────────────────── */}
          <aside className="lm-isearch__rail">
            <div className="lm-isearch__rail-head">
              <h2>{t('rail.filters')}</h2>
              <ClearRefinements translations={widget.clearRefinements} />
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
                  <h3>{t('rail.filterByCategory')}</h3>
                  <RefinementList
                    attribute="category_ids"
                    limit={8}
                    showMore
                    transformItems={transformCategoryFacetItems}
                    translations={widget.refinementList}
                  />
                </section>
              </>
            )}

            <BrandFacet categoryId={params.id} />

            <section className="lm-isearch__facet">
              <h3>{t('rail.price')}</h3>
              <RangeInput attribute="price" translations={widget.rangeInput} />
            </section>

            {/* Wave-19 coupon_flag / Wave-21 groupon_flag toggles ride the
                standard facetFilters path (adapter maps them onto the flat
                `<flag>=1` /srv/search params, OCS filters on the indexed
                fields) and round-trip the URL as ?coupon_flag=1 /
                ?groupon_flag=1 via searchRouting's toggle mapping. */}
            <section className="lm-isearch__facet">
              <h3>{t('rail.offers')}</h3>
              <ToggleRefinement attribute="coupon_flag" on={1} label={t('rail.hasCoupon')} />
              <ToggleRefinement attribute="groupon_flag" on={1} label={t('rail.groupBuy')} />
            </section>

            {/* Wave-27 eu_flag. Its own section, NOT "Offers": EU stock is where
                the goods are, not a price. The label states the measurement —
                the flag records what the last inventory probe found, and 0 means
                "not known to hold EU stock" (unprobed and probed-empty are
                indistinguishable), so there is deliberately no inverse toggle. */}
            <section className="lm-isearch__facet">
              <h3>{t('rail.delivery')}</h3>
              <ToggleRefinement attribute="eu_flag" on={1} label={t('rail.inEuStock')} />
            </section>

            {/* Every other facet group the backend returns (attributes, variant
                fields, …) — rendered dynamically so new OCS facets need no SPA change. */}
            <DynamicExtraFacets categoryId={params.id} />
          </aside>

          {/* ── Results ─────────────────────────────────────────────────── */}
          <section className="lm-isearch__results">
            <div className="lm-isearch__results-head">
              <Stats translations={widget.stats} />
              <label className="lm-isearch__sort">
                {t('sort.label')}
                <SortBy items={sortItems} />
              </label>
            </div>

            {showRelaxedBanner && (
              <div className="lm-isearch__relaxed" role="status">
                {t('relaxed', { query: relaxedQuery })}
              </div>
            )}

            <CurrentRefinements transformItems={transformCurrentRefinements} />

            {/* An outage (typed errno 502 / gateway unreachable — published by the
                search client as meta.unavailable) is NOT a relevance miss: say so
                honestly instead of showing the "no results" suggestions. */}
            <NoResultsBoundary fallback={meta.unavailable ? <SearchUnavailableState /> : <SearchEmptyState categoryId={params.id} />}>
              <Hits hitComponent={ProductHit} classNames={{ list: 'lm-isearch__grid' }} />

              <Pagination className="lm-isearch__pager" padding={2} translations={widget.pagination} />
            </NoResultsBoundary>
          </section>
        </div>
      </InstantSearch>

      <div className="lm-isearch__foot">
        <Link to="/">{t('continueShopping')}</Link>
      </div>
    </div>
  );
};

export default SearchView;
