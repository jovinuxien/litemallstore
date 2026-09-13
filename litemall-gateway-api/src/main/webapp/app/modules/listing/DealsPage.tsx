import { useTranslation } from 'app/i18n';
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

const DISCOUNT_BANDS: { key: string; lo?: number; hi?: number; range: string | null }[] = [
  { key: 'all', range: null },
  { key: 'band', lo: 10, hi: 25, range: '10,25' },
  { key: 'band', lo: 25, hi: 50, range: '25,50' },
  { key: 'band', lo: 50, hi: 70, range: '50,70' },
  { key: 'top', lo: 70, range: '70,100' },
];

const SORTS: { key: 'featured' | 'deepest' | 'endingSoon' | 'priceAsc'; value: string | null }[] = [
  { key: 'featured', value: null },
  { key: 'deepest', value: '-discount_pct' },
  { key: 'endingSoon', value: 'deal_end_epoch' },
  { key: 'priceAsc', value: 'price' },
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
  const { t } = useTranslation('content');
  const bandLabel = (b: (typeof DISCOUNT_BANDS)[number]) =>
    b.key === 'all' ? t('deals.allDeals') : b.key === 'top' ? t('deals.bandTop', { lo: b.lo }) : t('deals.band', { lo: b.lo, hi: b.hi });
  const [goods, setGoods] = useState<IGood[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [band, setBand] = useState<string | null>(null);
  const [liveOnly, setLiveOnly] = useState(false);
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
    if (liveOnly) p.deal_active = 1;
    if (category) p.category_names = category;
    if (sort) p.sort = sort;
    if (q) p.q = q;
    return p;
  }, [band, liveOnly, category, sort, q, page]);

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
        <h1 className='h4 mb-2'>{t('deals.title')}</h1>
        <form className='d-flex mb-2' style={{ maxWidth: 340 }} onSubmit={onSubmit}>
          <input
            className='form-control form-control-sm me-2'
            placeholder={t('deals.searchPlaceholder')}
            value={input}
            list='deals-suggest'
            onChange={e => setInput(e.target.value)}
            aria-label={t('deals.searchPlaceholder')}
          />
          <datalist id='deals-suggest'>
            {suggestions.map(s => (
              <option key={s} value={s} />
            ))}
          </datalist>
          <button type='submit' className='btn btn-sm btn-dark'>
            {t('deals.search')}
          </button>
        </form>
      </div>

      <div className='mb-1'>
        {DISCOUNT_BANDS.map(b => (
          <Chip key={b.range ?? 'all'} active={band === b.range} onClick={() => resetAnd(() => setBand(b.range))}>
            {bandLabel(b)}
          </Chip>
        ))}
        <Chip active={liveOnly} onClick={() => resetAnd(() => setLiveOnly(!liveOnly))}>
          {t('deals.limitedTime')}
        </Chip>
      </div>

      {categoryChips.length > 0 && (
        <div className='mb-1'>
          <Chip active={category === null} onClick={() => resetAnd(() => setCategory(null))}>
            {t('deals.allCategories')}
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
          {loading && page === 1 ? '…' : t('deals.count', { count: total })}
          {q && (
            <>
              {t('deals.forQuery', { query: q })}
              <button
                type='button'
                className='btn btn-link btn-sm p-0 align-baseline'
                onClick={() => {
                  setInput('');
                  resetAnd(() => setQ(''));
                }}
              >
                {t('deals.clear')}
              </button>
            </>
          )}
        </span>
        <select
          className='form-select form-select-sm w-auto'
          value={sort ?? ''}
          onChange={e => resetAnd(() => setSort(e.target.value || null))}
          aria-label={t('deals.sortAria')}
        >
          {SORTS.map(s => (
            <option key={s.key} value={s.value ?? ''}>
              {t(`deals.${s.key}`)}
            </option>
          ))}
        </select>
      </div>

      {loading && page === 1 ? (
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      ) : goods.length === 0 ? (
        <p className='text-muted text-center my-5'>{t('deals.none')}</p>
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
                {loading ? t('deals.loading') : t('deals.showMore')}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default DealsPage;
