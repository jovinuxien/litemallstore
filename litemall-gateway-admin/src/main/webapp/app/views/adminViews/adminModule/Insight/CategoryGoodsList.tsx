import { InsightSortKey, useGetInsightGoodsListQuery } from 'app/shared/reducers/private/services/insightApi';
import { PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import { fmtDay, fmtInt, fmtMoney, fmtPct } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import * as React from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';

// Wave 12: goods of one L1 category with the insight columns
// (/srv/private/admin/insight/goods/list). DEFAULT sort = arrival date
// (add_time desc); every sort option round-trips SERVER-side via the
// contract sort keys — no client-side reordering. Row → per-goods insight
// page.

const SORT_OPTIONS: { key: InsightSortKey; label: string }[] = [
  { key: 'add_time', label: 'Arrival date' },
  { key: 'retail_price', label: 'Price' },
  { key: 'stock', label: 'Stock availability' },
  { key: 'margin_pct', label: 'Margin' },
  { key: 'sales', label: 'Sales' },
];

const CategoryGoodsList: React.FC = () => {
  const { id } = useParams<'id'>();
  // Category name travels via Link state from the ranking page; fall back to
  // the id when the page is opened directly.
  const categoryName = (useLocation().state as { name?: string } | null)?.name;

  const [sort, setSort] = React.useState<InsightSortKey>('add_time');
  const [order, setOrder] = React.useState<'asc' | 'desc'>('desc');
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);

  const { data, isLoading, isFetching, isError, error } = useGetInsightGoodsListQuery(
    { categoryId: id ?? '', sort, order, page, limit },
    { skip: !id }
  );

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? (limit > 0 ? Math.ceil(total / limit) : 0);
  const errStatus = (error as { status?: number | string })?.status;

  return (
    <div className='app-container'>
      <div className='filter-container'>
        <h4 className='mb-0 filter-item'>{categoryName ? `${categoryName} — goods` : `Category #${id} — goods`}</h4>
        <select
          className='form-select filter-item'
          style={{ width: 190 }}
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
          style={{ width: 130 }}
          value={order}
          onChange={e => {
            setPage(1);
            setOrder(e.target.value as 'asc' | 'desc');
          }}
          aria-label='Sort order'
        >
          <option value='desc'>Descending</option>
          <option value='asc'>Ascending</option>
        </select>
        <select
          className='form-select filter-item'
          style={{ width: 120 }}
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
        <Link className='btn btn-outline-secondary filter-item' to='/admin/goods/categories'>
          ← All categories
        </Link>
        {isFetching && <Spinner />}
      </div>

      {isError && <div className='alert alert-danger'>Failed to load category goods{errStatus ? ` (${errStatus})` : ''}.</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Goods</th>
            <th className='text-end'>Retail</th>
            <th className='text-end'>Cost</th>
            <th className='text-end'>Margin</th>
            <th className='text-end'>Stock</th>
            <th>CJ availability</th>
            <th>Arrival</th>
            <th className='text-end'>Sales</th>
            <th>Deal</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={10} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={10} className='text-center text-muted py-5'>
                No goods in this category.
              </td>
            </tr>
          ) : (
            list.map(g => (
              <tr key={g.id}>
                <td>
                  {g.picUrl && <img src={g.picUrl} alt='' style={{ width: 40, height: 40, objectFit: 'cover', borderRadius: 4 }} className='me-2' />}
                  <Link to={`/admin/goods/${g.id}/insight`}>{g.name || `Goods #${g.id}`}</Link>
                  <span className='text-muted small ms-1'>#{g.id}</span>
                </td>
                <td className='text-end'>{fmtMoney(g.retailPrice)}</td>
                <td className='text-end'>{fmtMoney(g.cost)}</td>
                <td className='text-end'>
                  {fmtPct(g.marginPct)}
                  {g.marginAmount != null && <span className='text-muted small ms-1'>({fmtMoney(g.marginAmount)})</span>}
                </td>
                <td className='text-end'>{fmtInt(g.stockTotal)}</td>
                <td>{g.cjAvailable == null ? <Tag tag='info'>unknown</Tag> : g.cjAvailable ? <Tag tag='success'>available</Tag> : <Tag tag='danger'>unavailable</Tag>}</td>
                <td>{fmtDay(g.arrivalDate)}</td>
                <td className='text-end'>{fmtInt(g.salesQty)}</td>
                <td>{g.dealStatus ? <Tag tag={g.dealStatus === 'live' ? 'danger' : 'primary'}>{g.dealStatus}</Tag> : <span className='text-muted'>—</span>}</td>
                <td className='text-end'>
                  <Link to={`/admin/goods/${g.id}/insight`} className='btn btn-sm btn-outline-primary'>
                    Insight
                  </Link>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={pages} total={total} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />
    </div>
  );
};

export default CategoryGoodsList;
