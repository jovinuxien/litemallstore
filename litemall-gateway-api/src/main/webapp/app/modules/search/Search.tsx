import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import ProductCard from 'app/components/userComponents/card/ProductCard';
import { IFacetGroup } from 'app/shared/model/search/search.models';
import { searchProducts } from '../product/searchSlice';
import 'app/components/userComponents/card/product-card.scss';
import './search.scss';

const DEFAULT_SIZE = 12;
const SIZE_OPTIONS = [12, 24, 48];

// Query params handled explicitly; everything else in the URL is a facet filter
// keyed by its OCS field (category_ids, brand, price, attributes…).
const RESERVED = new Set(['q', 'sort', 'page', 'size']);

const FIELD_LABELS: Record<string, string> = {
  category_ids: 'Category',
  category_names: 'Category',
  brand: 'Brand',
  price: 'Price',
};
const humanize = (field: string): string => FIELD_LABELS[field] ?? field.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());

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
 * layout). The header search box and the home category flyout both land here.
 *
 * The URL is the single source of truth: `q`, `sort`, `page`, `size`, and one
 * query param per active facet filter keyed by its OCS field (`category_ids`,
 * `brand`, `price=min,max`, …). The sidebar renders whatever `filters[]` groups
 * the backend returns, so new facets need no SPA change. No SQL fallback — the
 * faceted view is OCS-only.
 */
const SearchView: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const params = useParams<{ id?: string }>();

  const q = searchParams.get('q') ?? '';
  const sort = searchParams.get('sort') ?? '';
  const page = Math.max(1, Number(searchParams.get('page') ?? '1') || 1);
  const size = Number(searchParams.get('size')) || DEFAULT_SIZE;

  // Active facet filters = every non-reserved query param. The /category/:id
  // path param maps to the OCS `category_ids` filter (unless already in the URL).
  const filters = useMemo(() => {
    const f: Record<string, string> = {};
    searchParams.forEach((value, key) => {
      if (!RESERVED.has(key) && value) f[key] = value;
    });
    if (params.id && f.category_ids == null) f.category_ids = params.id;
    return f;
  }, [searchParams, params.id]);
  const filterKey = JSON.stringify(filters);

  const { data, loading, errorMessage } = useAppSelector(state => state.search);
  const { list, total, pages, facetGroups, sortOptions } = data;

  useEffect(() => {
    dispatch(searchProducts({ q: q || undefined, page, size, sort: sort || null, filters }));
    // filterKey stands in for the (stable) filters object identity.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dispatch, q, sort, page, size, filterKey]);

  // Build the canonical /search URL. `null` in `filters` removes that key.
  const navigateWith = (mut: { q?: string; sort?: string | null; page?: number; size?: number; filters?: Record<string, string | null> }) => {
    const next = { ...filters, ...(mut.filters ?? {}) };
    const sp = new URLSearchParams();
    const nq = mut.q !== undefined ? mut.q : q;
    if (nq) sp.set('q', nq);
    const ns = mut.sort !== undefined ? mut.sort : sort;
    if (ns) sp.set('sort', ns);
    const nsize = mut.size ?? size;
    if (nsize !== DEFAULT_SIZE) sp.set('size', String(nsize));
    Object.entries(next).forEach(([k, v]) => {
      if (v != null && String(v).trim() !== '') sp.set(k, String(v));
    });
    const np = mut.page ?? 1;
    if (np > 1) sp.set('page', String(np));
    navigate(`/search?${sp.toString()}`);
  };

  const selectedValues = (field: string): string[] =>
    (filters[field] ?? '')
      .split(',')
      .map(s => s.trim())
      .filter(Boolean);

  const toggleTerm = (field: string, value: string) => {
    const cur = selectedValues(field);
    const nextVals = cur.includes(value) ? cur.filter(v => v !== value) : [...cur, value];
    navigateWith({ filters: { [field]: nextVals.length ? nextVals.join(',') : null }, page: 1 });
  };

  // Price interval — local inputs, applied as `price=min,max`.
  const [minPrice, setMinPrice] = useState('');
  const [maxPrice, setMaxPrice] = useState('');
  useEffect(() => {
    const [lo = '', hi = ''] = (filters.price ?? '').split(',');
    setMinPrice(lo);
    setMaxPrice(hi);
  }, [filters.price]);

  const applyPrice = () => {
    const lo = minPrice.trim();
    const hi = maxPrice.trim();
    navigateWith({ filters: { price: !lo && !hi ? null : `${lo || '0'},${hi || '999999'}` }, page: 1 });
  };

  const clearAll = () => navigateWith({ q, sort: null, filters: Object.fromEntries(Object.keys(filters).map(k => [k, null])) });

  const activeChips = useMemo(
    () =>
      Object.entries(filters).map(([field, value]) => ({
        label: `${humanize(field)}: ${value}`,
        onRemove: () => navigateWith({ filters: { [field]: null }, page: 1 }),
      })),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [filterKey]
  );
  const hasFilters = activeChips.length > 0;

  const renderFacetGroup = (group: IFacetGroup) => {
    const isPrice = group.field === 'price' || group.type === 'interval';
    if (isPrice) {
      return (
        <FilterGroup key={group.field} title={humanize(group.field)}>
          <div className="lm-rail__price">
            <input type="number" min={0} placeholder="Min" value={minPrice} onChange={e => setMinPrice(e.target.value)} />
            <span>–</span>
            <input type="number" min={0} placeholder="Max" value={maxPrice} onChange={e => setMaxPrice(e.target.value)} />
          </div>
          <button type="button" className="lm-rail__apply" onClick={applyPrice}>
            Apply
          </button>
        </FilterGroup>
      );
    }
    const selected = selectedValues(group.field);
    return (
      <FilterGroup key={group.field} title={humanize(group.field)}>
        {group.entries.length === 0 && <p className="lm-rail__empty">No options</p>}
        <ul className="lm-rail__list" style={{ maxHeight: 240, overflowY: 'auto' }}>
          {group.entries.map(entry => (
            <li key={`${group.field}-${entry.value}`}>
              <label className="lm-rail__opt">
                <input type="checkbox" checked={selected.includes(entry.value) || entry.selected} onChange={() => toggleTerm(group.field, entry.value)} />
                <span>
                  {entry.value} <em>({entry.count})</em>
                </span>
              </label>
            </li>
          ))}
        </ul>
      </FilterGroup>
    );
  };

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
          {facetGroups.length === 0 && <p className="lm-rail__empty">No filters available for these results.</p>}
          {facetGroups.map(renderFacetGroup)}
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
                <select value={size} onChange={e => navigateWith({ size: Number(e.target.value), page: 1 })}>
                  {SIZE_OPTIONS.map(n => (
                    <option key={n} value={n}>
                      {n}
                    </option>
                  ))}
                </select>
              </label>
              {sortOptions.length > 0 && (
                <label className="lm-results__sort">
                  Sort by
                  <select value={sort} onChange={e => navigateWith({ sort: e.target.value || null, page: 1 })}>
                    <option value="">Relevance</option>
                    {sortOptions.map(o => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                </label>
              )}
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

          {loading !== 'pending' && list.length === 0 && !errorMessage && <div className="lm-results__empty">No products match your filters.</div>}

          <div className="lm-results__grid">
            {list.map((product, i) => (
              <ProductCard key={product.id ?? `r-${i}`} product={product} />
            ))}
          </div>

          {pages > 1 && (
            <nav className="lm-pager" aria-label="pagination">
              <button type="button" disabled={page <= 1} onClick={() => navigateWith({ page: page - 1 })}>
                ‹ Prev
              </button>
              {Array.from({ length: pages }, (_, i) => i + 1).map(p => (
                <button key={p} type="button" className={p === page ? 'is-active' : ''} onClick={() => navigateWith({ page: p })}>
                  {p}
                </button>
              ))}
              <button type="button" disabled={page >= pages} onClick={() => navigateWith({ page: page + 1 })}>
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
