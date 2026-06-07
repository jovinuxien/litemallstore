import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getSecondCategories } from 'app/modules/Category/categorySlice';
import ProductCard, { priceNum } from 'app/components/userComponents/card/ProductCard';
import { IGood } from 'app/shared/model/product/product.model';
import { SearchSort } from 'app/shared/model/search/search.models';
import { searchProducts } from '../product/searchSlice';
import 'app/components/userComponents/card/product-card.scss';
import './search.scss';

const DEFAULT_SIZE = 12;
const SIZE_OPTIONS = [12, 24, 48];

const SORT_OPTIONS: { value: SearchSort; label: string }[] = [
  { value: 'relevance', label: 'Relevance' },
  { value: 'price_asc', label: 'Price: low to high' },
  { value: 'price_desc', label: 'Price: high to low' },
];

/** Collapsible filter group — Amazon-style refinement section. */
const FilterGroup: React.FC<{ title: string; defaultOpen?: boolean; children: React.ReactNode }> = ({ title, defaultOpen = true, children }) => {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="lm-filter">
      <button type="button" className="lm-filter__head" onClick={() => setOpen(o => !o)} aria-expanded={open}>
        <span>{title}</span>
        <span className={`lm-filter__chev${open ? ' lm-filter__chev--open' : ''}`}>›</span>
      </button>
      {open && <div className="lm-filter__body">{children}</div>}
    </div>
  );
};

/**
 * Dedicated faceted search / browse page (Teal & Coral theme, Amazon browse-node
 * layout). The search bar and the home category flyout both land here.
 *
 * The URL is the single source of truth for ALL search state (q, category, brand,
 * price, sort, page, page-size) so results are shareable and the back button
 * works — mirroring Elastic Search UI's `trackUrlState`. Free-text price inputs
 * are kept in local state and pushed to the URL on a short debounce so typing
 * doesn't spam history. Results + facet buckets come from goods-management's OCS
 * `/srv/search`; brand/category buckets stay empty until that worktree surfaces
 * aggregations (FOLLOW-UP) — the rail is empty-safe.
 */
const SearchView: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const params = useParams<{ id?: string }>();

  // ── derive every value from the URL (category also accepts /category/:id) ──
  const q = searchParams.get('q') ?? '';
  const categoryParam = params.id ?? searchParams.get('category');
  const category = categoryParam ? Number(categoryParam) : null;
  const selectedBrands = useMemo(
    () =>
      (searchParams.get('brand') ?? '')
        .split(',')
        .filter(Boolean)
        .map(Number),
    [searchParams]
  );
  const minPrice = searchParams.get('minPrice') ?? '';
  const maxPrice = searchParams.get('maxPrice') ?? '';
  const sort = (searchParams.get('sort') as SearchSort) || 'relevance';
  const page = Number(searchParams.get('page')) || 1;
  const size = Number(searchParams.get('size')) || DEFAULT_SIZE;

  const { data, loading, errorMessage } = useAppSelector(state => state.search);
  const { list, total, pages, facets } = data;
  const subCategories = useAppSelector(state => (category != null ? state.category.data.secondCategoriesById[category] : undefined)) ?? [];

  /**
   * Rebuild the canonical /search URL from the current state plus `patch`.
   * Any filter change resets to page 1 unless `keepPage` is set (pager only).
   */
  const apply = (
    patch: Partial<{ q: string; category: number | null; brands: number[]; minPrice: string; maxPrice: string; sort: SearchSort; size: number; page: number }>,
    keepPage = false
  ) => {
    const sp = new URLSearchParams();
    const qv = patch.q ?? q;
    if (qv) sp.set('q', qv);
    const cv = 'category' in patch ? patch.category : category;
    if (cv != null) sp.set('category', String(cv));
    const bv = patch.brands ?? selectedBrands;
    if (bv.length) sp.set('brand', bv.join(','));
    const mn = patch.minPrice ?? minPrice;
    if (mn) sp.set('minPrice', mn);
    const mx = patch.maxPrice ?? maxPrice;
    if (mx) sp.set('maxPrice', mx);
    const sv = patch.sort ?? sort;
    if (sv && sv !== 'relevance') sp.set('sort', sv);
    const sz = patch.size ?? size;
    if (sz !== DEFAULT_SIZE) sp.set('size', String(sz));
    const pg = keepPage ? patch.page ?? page : patch.page ?? 1;
    if (pg > 1) sp.set('page', String(pg));
    navigate(`/search?${sp.toString()}`);
  };

  // Price inputs are local (debounced → URL) so keystrokes don't spam history.
  const [minLocal, setMinLocal] = useState(minPrice);
  const [maxLocal, setMaxLocal] = useState(maxPrice);
  useEffect(() => setMinLocal(minPrice), [minPrice]);
  useEffect(() => setMaxLocal(maxPrice), [maxPrice]);
  useEffect(() => {
    if (minLocal === minPrice && maxLocal === maxPrice) return undefined;
    const t = setTimeout(() => apply({ minPrice: minLocal || '', maxPrice: maxLocal || '' }), 450);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [minLocal, maxLocal]);

  // Lazy-load the active category's children so they show as drill-down links.
  useEffect(() => {
    if (category != null) dispatch(getSecondCategories(category));
  }, [dispatch, category]);

  // Fetch results whenever any URL-derived parameter changes.
  useEffect(() => {
    dispatch(
      searchProducts({
        q: q || undefined,
        category,
        brands: selectedBrands,
        minPrice: minPrice === '' ? null : Number(minPrice),
        maxPrice: maxPrice === '' ? null : Number(maxPrice),
        sort,
        page,
        size,
      })
    );
  }, [dispatch, q, category, selectedBrands, minPrice, maxPrice, sort, page, size]);

  const toggleBrand = (id: number) =>
    apply({ brands: selectedBrands.includes(id) ? selectedBrands.filter(b => b !== id) : [...selectedBrands, id] });

  const setCategory = (id: number | null) => apply({ category: id });

  const clearAll = () => {
    const sp = new URLSearchParams();
    if (q) sp.set('q', q);
    navigate(`/search?${sp.toString()}`);
  };

  // Interim client-side sort over the current page (server sort is a goods-
  // management follow-up). Relevance keeps the backend order as-is.
  const sortedList = useMemo(() => {
    const items = list as IGood[];
    if (sort === 'relevance') return items;
    const dir = sort === 'price_asc' ? 1 : -1;
    return [...items].sort((a, b) => (priceNum(a.retailPrice) - priceNum(b.retailPrice)) * dir);
  }, [list, sort]);

  const activeChips = useMemo(() => {
    const chips: { label: string; onRemove: () => void }[] = [];
    if (category != null) {
      const name = facets.categories.find(c => c.id === category)?.name ?? subCategories.find(c => c.id === category)?.name ?? `Category ${category}`;
      chips.push({ label: `Category: ${name}`, onRemove: () => setCategory(null) });
    }
    selectedBrands.forEach(b => {
      const name = facets.brands.find(br => br.id === b)?.name ?? `Brand ${b}`;
      chips.push({ label: `Brand: ${name}`, onRemove: () => toggleBrand(b) });
    });
    if (minPrice !== '' || maxPrice !== '') {
      chips.push({ label: `Price: ${minPrice || '0'} – ${maxPrice || '∞'}`, onRemove: () => apply({ minPrice: '', maxPrice: '' }) });
    }
    return chips;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [category, selectedBrands, minPrice, maxPrice, facets, subCategories]);

  const hasFilters = activeChips.length > 0;

  return (
    <div className="lm-search">
      <div className="lm-search__container">
        {/* ── Left filter rail ────────────────────────────────────────── */}
        <aside className="lm-rail">
          <div className="lm-rail__head">
            <h2 className="lm-rail__title">Filters</h2>
            {hasFilters && (
              <button type="button" className="lm-rail__clear" onClick={clearAll}>
                Clear all
              </button>
            )}
          </div>

          <FilterGroup title="Category">
            {subCategories.length > 0 && (
              <ul className="lm-rail__list lm-rail__list--links">
                {subCategories.map(sc => (
                  <li key={sc.id}>
                    <button type="button" className="lm-rail__link" onClick={() => setCategory(sc.id)}>
                      {sc.name}
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {facets.categories.length > 0 && (
              <ul className="lm-rail__list">
                {facets.categories.map(c => (
                  <li key={c.id}>
                    <label className="lm-rail__opt">
                      <input type="radio" name="category-facet" checked={category === c.id} onChange={() => setCategory(c.id)} />
                      <span>
                        {c.name} <em>({c.count})</em>
                      </span>
                    </label>
                  </li>
                ))}
              </ul>
            )}
            {subCategories.length === 0 && facets.categories.length === 0 && <p className="lm-rail__empty">No subcategories</p>}
          </FilterGroup>

          <FilterGroup title="Brand">
            {facets.brands.length === 0 && <p className="lm-rail__empty">No brands</p>}
            <ul className="lm-rail__list">
              {facets.brands.map(b => (
                <li key={b.id}>
                  <label className="lm-rail__opt">
                    <input type="checkbox" checked={selectedBrands.includes(b.id)} onChange={() => toggleBrand(b.id)} />
                    <span>
                      {b.name} <em>({b.count})</em>
                    </span>
                  </label>
                </li>
              ))}
            </ul>
          </FilterGroup>

          <FilterGroup title="Price">
            {facets.price && (
              <p className="lm-rail__hint">
                Range: ${facets.price.min} – ${facets.price.max}
              </p>
            )}
            <div className="lm-rail__price">
              <input type="number" min={0} placeholder="Min" value={minLocal} onChange={e => setMinLocal(e.target.value)} />
              <span>–</span>
              <input type="number" min={0} placeholder="Max" value={maxLocal} onChange={e => setMaxLocal(e.target.value)} />
            </div>
          </FilterGroup>
        </aside>

        {/* ── Results ─────────────────────────────────────────────────── */}
        <section className="lm-results">
          <div className="lm-results__bar">
            <span className="lm-results__count">
              <strong>{total}</strong> results{q ? ` for “${q}”` : ''}
              {loading === 'pending' && <span className="lm-results__spin" aria-label="loading" />}
            </span>
            <div className="lm-results__controls">
              <label className="lm-results__sort">
                Show
                <select value={size} onChange={e => apply({ size: Number(e.target.value) })}>
                  {SIZE_OPTIONS.map(n => (
                    <option key={n} value={n}>
                      {n}
                    </option>
                  ))}
                </select>
              </label>
              <label className="lm-results__sort">
                Sort by
                <select value={sort} onChange={e => apply({ sort: e.target.value as SearchSort })}>
                  {SORT_OPTIONS.map(o => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          </div>

          {hasFilters && (
            <div className="lm-chips">
              {activeChips.map((chip, i) => (
                <span key={i} className="lm-chip">
                  {chip.label}
                  <button type="button" className="lm-chip__x" onClick={chip.onRemove} aria-label="remove filter">
                    ×
                  </button>
                </span>
              ))}
            </div>
          )}

          {errorMessage && <div className="lm-results__error">{errorMessage}</div>}

          {loading !== 'pending' && sortedList.length === 0 && !errorMessage && (
            <div className="lm-results__empty">No products match your filters.</div>
          )}

          <div className="lm-results__grid">
            {sortedList.map((product, i) => (
              <ProductCard key={product.id ?? `r-${i}`} product={product} />
            ))}
          </div>

          {pages > 1 && (
            <nav className="lm-pager" aria-label="pagination">
              <button type="button" disabled={page <= 1} onClick={() => apply({ page: Math.max(1, page - 1) }, true)}>
                ‹ Prev
              </button>
              {Array.from({ length: pages }, (_, i) => i + 1).map(p => (
                <button key={p} type="button" className={p === page ? 'is-active' : ''} onClick={() => apply({ page: p }, true)}>
                  {p}
                </button>
              ))}
              <button type="button" disabled={page >= pages} onClick={() => apply({ page: Math.min(pages, page + 1) }, true)}>
                Next ›
              </button>
            </nav>
          )}
        </section>
      </div>
      <div className="lm-search__foot">
        <Link to="/">← Continue shopping</Link>
      </div>
    </div>
  );
};

export default SearchView;
