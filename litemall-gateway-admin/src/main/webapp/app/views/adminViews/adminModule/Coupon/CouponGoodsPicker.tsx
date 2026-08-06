import { InsightSortKey, useGetInsightCategoriesQuery, useGetInsightGoodsListQuery } from 'app/shared/reducers/private/services/insightApi';
import { PAGE_SIZES, Pagination, Spinner } from 'app/views/adminViews/adminModule/_shared/crudUi';
import { fmtMoney, fmtPct } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import { MAX_SCOPE_GOODS } from './couponFormat';
import * as React from 'react';

// Wave 18: product picker for a product-scoped coupon (goodsType 2), reusing
// the goods-management insight surface — category select feeds
// /srv/private/admin/insight/goods/list?categoryId= so every row shows
// picture, retail price and margin. Multi-select is capped at 500 (the
// Wave-18 contract cap). The form owns the id list; names are cached here so
// the selection chips stay readable across pages (ids loaded from an existing
// coupon render as '#id' until their page is visited).

const SORT_OPTIONS: { key: InsightSortKey; label: string }[] = [
  { key: 'add_time', label: 'Arrival date' },
  { key: 'retail_price', label: 'Price' },
  { key: 'margin_pct', label: 'Margin' },
  { key: 'stock', label: 'Stock' },
  { key: 'sales', label: 'Sales' },
];

// Chips shown before collapsing into "+N more" — 500 buttons is not a UI.
const MAX_CHIPS = 20;

interface Props {
  value: number[];
  onChange: (ids: number[]) => void;
}

const CouponGoodsPicker: React.FC<Props> = ({ value, onChange }) => {
  const { data: categories } = useGetInsightCategoriesQuery();

  const [categoryId, setCategoryId] = React.useState('');
  const [sort, setSort] = React.useState<InsightSortKey>('add_time');
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(10);
  const { data: goods, isLoading, isFetching } = useGetInsightGoodsListQuery({ categoryId, sort, order: 'desc', page, limit }, { skip: !categoryId });

  const [names, setNames] = React.useState<Map<number, string>>(new Map());
  const selected = React.useMemo(() => new Set(value), [value]);

  const toggle = (id: number, name?: string) => {
    if (selected.has(id)) {
      onChange(value.filter(x => x !== id));
      return;
    }
    if (value.length >= MAX_SCOPE_GOODS) return;
    if (name) setNames(prev => new Map(prev).set(id, name));
    onChange([...value, id]);
  };

  const list = goods?.list ?? [];
  const total = goods?.total ?? 0;
  const pages = goods?.pages ?? (limit > 0 ? Math.ceil(total / limit) : 0);
  const chips = value.slice(0, MAX_CHIPS);

  return (
    <div className='border rounded p-2'>
      <div className='filter-container'>
        <select
          className='form-select filter-item'
          style={{ width: 260 }}
          value={categoryId}
          onChange={e => {
            setPage(1);
            setCategoryId(e.target.value);
          }}
          aria-label='Browse category'
        >
          <option value=''>Browse a category…</option>
          {(categories?.list ?? []).map(c => (
            <option key={c.categoryId} value={c.categoryId}>
              {c.name} ({c.onSaleCount} on sale)
            </option>
          ))}
        </select>
        <select
          className='form-select filter-item'
          style={{ width: 170 }}
          value={sort}
          onChange={e => {
            setPage(1);
            setSort(e.target.value as InsightSortKey);
          }}
          aria-label='Sort by'
        >
          {SORT_OPTIONS.map(o => (
            <option key={o.key} value={o.key}>
              Sort: {o.label}
            </option>
          ))}
        </select>
        <select
          className='form-select filter-item'
          style={{ width: 110 }}
          value={limit}
          onChange={e => {
            setPage(1);
            setLimit(Number(e.target.value));
          }}
          aria-label='Page size'
        >
          {PAGE_SIZES.map(n => (
            <option key={n} value={n}>
              {n} / page
            </option>
          ))}
        </select>
        <span className='filter-item text-muted'>
          {value.length}/{MAX_SCOPE_GOODS} selected
        </span>
        {value.length > 0 && (
          <button type='button' className='btn btn-sm btn-outline-danger filter-item' onClick={() => onChange([])}>
            Clear all
          </button>
        )}
        {isFetching && <Spinner />}
      </div>

      {value.length > 0 && (
        <div className='mb-2 d-flex flex-wrap gap-1'>
          {chips.map(id => (
            <button key={id} type='button' className='btn btn-sm btn-outline-secondary' onClick={() => toggle(id)} title='Remove from coupon scope'>
              {names.get(id) || `#${id}`} ✕
            </button>
          ))}
          {value.length > MAX_CHIPS && <span className='text-muted small align-self-center'>+{value.length - MAX_CHIPS} more</span>}
        </div>
      )}

      {!categoryId ? (
        <div className='text-muted py-3'>Choose a category to browse its products.</div>
      ) : (
        <>
          <table className='el-table'>
            <thead>
              <tr>
                <th style={{ width: 34 }} />
                <th>Goods</th>
                <th className='text-end'>Retail</th>
                <th className='text-end'>Cost</th>
                <th className='text-end'>Margin</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr>
                  <td colSpan={5} className='text-center p-4'>
                    <span className='spinner-border text-primary' role='status' />
                  </td>
                </tr>
              ) : list.length === 0 ? (
                <tr>
                  <td colSpan={5} className='text-center text-muted py-4'>
                    No goods in this category.
                  </td>
                </tr>
              ) : (
                list.map(g => {
                  const checked = selected.has(g.id);
                  return (
                    <tr key={g.id}>
                      <td>
                        <input
                          type='checkbox'
                          className='form-check-input'
                          checked={checked}
                          disabled={!checked && value.length >= MAX_SCOPE_GOODS}
                          onChange={() => toggle(g.id, g.name)}
                          aria-label={`Select ${g.name || g.id}`}
                        />
                      </td>
                      <td>
                        {g.picUrl && <img src={g.picUrl} alt='' style={{ width: 40, height: 40, objectFit: 'cover', borderRadius: 4 }} className='me-2' />}
                        {g.name || `Goods #${g.id}`}
                        <span className='text-muted small ms-1'>#{g.id}</span>
                      </td>
                      <td className='text-end'>{fmtMoney(g.retailPrice)}</td>
                      <td className='text-end'>{fmtMoney(g.cost)}</td>
                      <td className='text-end'>{fmtPct(g.marginPct)}</td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
          <Pagination page={page} pages={pages} total={total} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />
        </>
      )}
    </div>
  );
};

export default CouponGoodsPicker;
