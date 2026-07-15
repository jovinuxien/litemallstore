import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Spinner } from 'react-bootstrap';

import ProductCard, { goodId } from 'app/components/userComponents/card/ProductCard';
import { baseAxios, SRV, unwrap } from 'app/shared/api';
import { IGood } from 'app/shared/model/product/product.model';
import 'app/shared/scss/content.scss';

/**
 * Today's Deals — Amazon goldbox-style discovery over the OCS deal signals.
 * Everything on this page is ONE `/srv/search` pipeline call scoped by
 * `deal_flag=1` (index-time markdown ≥ threshold):
 * - discount chips issue CLOSED-range filters (`discount_pct=25,50`) — OCS
 *   parses `min,max`; the labels live here, not in a facet (the field is
 *   Filter-usage, deliberately not a Facet, so the general search rail stays
 *   clean);
 * - category chips come from the unfiltered first response's category facet;
 * - default ordering is the searcher's scored browse (discount depth ×
 *   popularity × rating multiplied into relevance) — "Featured";
 * - search-within-deals is the same call with `q`, with a lightweight
 *   search-as-suggest datalist (the OCS suggest service can't scope to deals
 *   without a custom plugin, so top deal titles serve as completions).
 */

interface SearchData {
  goodsList?: IGood[];
  total?: number;
  totalPages?: number;
  filters?: { field?: string; entries?: { value?: string; count?: number }[] }[];
}

const PAGE_SIZE = 24;

const DISCOUNT_BANDS: { label: string; range: string | null }[] = [
  { label: 'All deals', range: null },
  { label: '10–25% off', range: '10,25' },
  { label: '25–50% off', range: '25,50' },
  { label: '50–70% off', range: '50,70' },
  { label: '70% off or more', range: '70,100' },
];

const SORTS: { label: string; value: string | null }[] = [
  { label: 'Featured', value: null },
  { label: 'Deepest discount', value: '-discount_pct' },
  { label: 'Price: low to high', value: 'price' },
];

const Chip: React.FC<{ active: boolean; onClick: () => void; children: React.ReactNode }> = ({ active, onClick, children }) => (
  <button
    type='button'
    className={`btn btn-sm rounded-pill me-2 mb-2 ${active ? 'btn-dark' : 'btn-outline-secondary'}`}
    onClick={onClick}
  >
    {children}
  </button>
);

const DealsPage: React.FC = () => {
  const [goods, setGoods] = useState<IGood[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [band, setBand] = useState<string | null>(null);
  const [category, setCategory] = useState<string | null>(null);
  const [sort, setSort] = useState<string | null>(null);
  const [q, setQ] = useState('');
  const [input, setInput] = useState('');
  const [suggestions, setSuggestions] = useState<string[]>([]);
  // Category chips are captured ONCE from the unfiltered response so they don't
  // collapse to the selected category after filtering.
  const [categoryChips, setCategoryChips] = useState<string[]>([]);
  const requestSeq = useRef(0);

  const params = useMemo(() => {
    const p: Record<string, string | number> = { deal_flag: 1, size: PAGE_SIZE, page };
    if (band) p.discount_pct = band;
    if (category) p.category_names = category;
    if (sort) p.sort = sort;
    if (q) p.q = q;
    return p;
  }, [band, category, sort, q, page]);

  useEffect(() => {
    const seq = ++requestSeq.current;
    setLoading(true);
    unwrap<SearchData>(baseAxios.get(`${SRV}/search`, { params }))
      .then(data => {
        if (seq !== requestSeq.current) return;
        const list = data?.goodsList ?? [];
        setGoods(prev => (page > 1 ? [...prev, ...list] : list));
        setTotal(data?.total ?? 0);
        setTotalPages(data?.totalPages ?? 0);
        setCategoryChips(prev => {
          if (prev.length > 0 || band || category || q) return prev;
          const cats = (data?.filters ?? []).find(f => f.field === 'category_names');
          return (cats?.entries ?? [])
            .map(e => e.value)
            .filter((v): v is string => !!v)
            .slice(0, 12);
        });
      })
      .catch(() => {
        if (seq === requestSeq.current && page === 1) {
          setGoods([]);
          setTotal(0);
          setTotalPages(0);
        }
      })
      .finally(() => seq === requestSeq.current && setLoading(false));
  }, [params]);

  // Search-as-suggest: top deal titles for the typed prefix (debounced).
  useEffect(() => {
    if (!input.trim()) {
      setSuggestions([]);
      return undefined;
    }
    const t = setTimeout(() => {
      unwrap<SearchData>(baseAxios.get(`${SRV}/search`, { params: { deal_flag: 1, q: input.trim(), size: 5, page: 1 } }))
        .then(data =>
          setSuggestions(((data?.goodsList ?? []) as { name?: string }[]).map(g => g.name).filter((n): n is string => !!n))
        )
        .catch(() => setSuggestions([]));
    }, 250);
    return () => clearTimeout(t);
  }, [input]);

  const resetAnd = (fn: () => void) => {
    setPage(1);
    fn();
  };

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    resetAnd(() => setQ(input.trim()));
  };

  return (
    <div className='container my-4'>
      <div className='d-flex flex-wrap align-items-center justify-content-between mb-2'>
        <h1 className='h4 mb-2'>Today&rsquo;s Deals</h1>
        <form className='d-flex mb-2' style={{ maxWidth: 340 }} onSubmit={onSubmit}>
          <input
            className='form-control form-control-sm me-2'
            placeholder='Search within deals'
            value={input}
            list='deals-suggest'
            onChange={e => setInput(e.target.value)}
            aria-label='Search within deals'
          />
          <datalist id='deals-suggest'>
            {suggestions.map(s => (
              <option key={s} value={s} />
            ))}
          </datalist>
          <button type='submit' className='btn btn-sm btn-dark'>
            Search
          </button>
        </form>
      </div>

      <div className='mb-1'>
        {DISCOUNT_BANDS.map(b => (
          <Chip key={b.label} active={band === b.range} onClick={() => resetAnd(() => setBand(b.range))}>
            {b.label}
          </Chip>
        ))}
      </div>

      {categoryChips.length > 0 && (
        <div className='mb-1'>
          <Chip active={category === null} onClick={() => resetAnd(() => setCategory(null))}>
            All categories
          </Chip>
          {categoryChips.map(c => (
            <Chip key={c} active={category === c} onClick={() => resetAnd(() => setCategory(category === c ? null : c))}>
              {c}
            </Chip>
          ))}
        </div>
      )}

      <div className='d-flex align-items-center justify-content-between mb-3'>
        <span className='text-muted small'>
          {loading && page === 1 ? '…' : `${total} deal${total === 1 ? '' : 's'}`}
          {q && (
            <>
              {' '}
              for &ldquo;{q}&rdquo;{' '}
              <button
                type='button'
                className='btn btn-link btn-sm p-0 align-baseline'
                onClick={() => {
                  setInput('');
                  resetAnd(() => setQ(''));
                }}
              >
                clear
              </button>
            </>
          )}
        </span>
        <select
          className='form-select form-select-sm w-auto'
          value={sort ?? ''}
          onChange={e => resetAnd(() => setSort(e.target.value || null))}
          aria-label='Sort deals'
        >
          {SORTS.map(s => (
            <option key={s.label} value={s.value ?? ''}>
              {s.label}
            </option>
          ))}
        </select>
      </div>

      {loading && page === 1 ? (
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      ) : goods.length === 0 ? (
        <p className='text-muted text-center my-5'>No deals match right now — check back soon.</p>
      ) : (
        <>
          <div className='lm-grid'>
            {goods.map((g, i) => (
              <ProductCard key={`deal-${goodId(g) ?? i}`} product={g} />
            ))}
          </div>
          {page < totalPages && (
            <div className='text-center my-4'>
              <button type='button' className='btn btn-outline-dark' disabled={loading} onClick={() => setPage(p => p + 1)}>
                {loading ? 'Loading…' : 'Show more deals'}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default DealsPage;
