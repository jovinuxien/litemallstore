import {
  dealOpMessage,
  IDeal,
  useDeleteDealMutation,
  useListDealsQuery,
  useUpdateDealMutation,
} from 'app/shared/reducers/private/services/adminDealApi';
import { PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Flash Deals admin list (adminDealApi → /srv/private/admin/deal through the
// gateway). Paged .el-table in the BrandList/FreightTemplateList look: goods
// thumbnail + name, deal vs original price, stock (0 = uncapped), sales, the
// start..stop window, an enabled toggle and a red LIVE badge for deals that
// are currently running. Business errors (errno 650/651/652) surface their
// errmsg inline instead of navigating away.

const fmtPrice = (v?: number | null): string => (v == null ? '—' : Number(v).toFixed(2));
// ISO LocalDateTime "2026-07-15T18:00:00" → "2026-07-15 18:00:00" for display.
const fmtTime = (v?: string): string => (v ? v.replace('T', ' ') : '—');

const DealList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);

  const { data, isLoading, isFetching, isError, error } = useListDealsQuery({ page, limit });
  const [deleteDeal, { isLoading: deleting }] = useDeleteDealMutation();
  const [updateDeal, { isLoading: toggling }] = useUpdateDealMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = limit > 0 ? Math.ceil(total / limit) : 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onDelete = async (deal: IDeal) => {
    if (!window.confirm(`Delete flash deal "${deal.goodsName || `#${deal.id}`}"?`)) return;
    setActionError(null);
    const res = await deleteDeal({ id: deal.id as number });
    const msg = dealOpMessage(res);
    if (msg) setActionError(msg);
  };

  const onToggleEnabled = async (deal: IDeal) => {
    setActionError(null);
    const res = await updateDeal({ id: deal.id as number, enabled: !deal.enabled });
    const msg = dealOpMessage(res);
    if (msg) setActionError(msg);
  };

  return (
    <div className='app-container'>
      <div className='filter-container'>
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
        <Link className='btn btn-success filter-item' to='/admin/promotion/deal/create'>
          + New flash deal
        </Link>
        {isFetching && <Spinner />}
      </div>

      {isError && <div className='alert alert-danger'>Failed to load flash deals{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th style={{ width: 70 }}>ID</th>
            <th>Goods</th>
            <th className='text-end'>Deal price</th>
            <th className='text-end'>Original</th>
            <th className='text-end'>Stock</th>
            <th className='text-end'>Sales</th>
            <th>Window</th>
            <th>Status</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={9} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={9} className='text-center text-muted py-5'>
                No flash deals yet.
              </td>
            </tr>
          ) : (
            list.map(deal => (
              <tr key={deal.id}>
                <td>#{deal.id}</td>
                <td>
                  {deal.picUrl && (
                    <img
                      src={deal.picUrl}
                      alt=''
                      style={{ width: 40, height: 40, objectFit: 'cover', borderRadius: 4 }}
                      className='me-2'
                    />
                  )}
                  <Link to={`/admin/promotion/deal/${deal.id}`}>{deal.goodsName || `Goods #${deal.goodsId}`}</Link>
                  <span className='text-muted small ms-1'>(goods #{deal.goodsId})</span>
                </td>
                <td className='text-end'>{fmtPrice(deal.dealPrice)}</td>
                <td className='text-end'>{fmtPrice(deal.originalRetailPrice)}</td>
                <td className='text-end'>{deal.stock === 0 ? <span className='text-muted'>uncapped</span> : deal.stock ?? '—'}</td>
                <td className='text-end'>{deal.sales ?? 0}</td>
                <td>
                  <span className='small'>
                    {fmtTime(deal.startTime)}
                    <span className='text-muted'> → </span>
                    {fmtTime(deal.stopTime)}
                  </span>
                </td>
                <td>
                  {deal.enabled ? <Tag tag='success'>enabled</Tag> : <Tag tag='info'>disabled</Tag>}
                  {deal.live && (
                    <span className='ms-1'>
                      <Tag tag='danger'>LIVE</Tag>
                    </span>
                  )}
                </td>
                <td className='text-end'>
                  <Link to={`/admin/promotion/deal/${deal.id}`} className='btn btn-sm btn-outline-primary me-1'>
                    Edit
                  </Link>
                  <button className='btn btn-sm btn-outline-secondary me-1' disabled={toggling} onClick={() => onToggleEnabled(deal)}>
                    {deal.enabled ? 'Disable' : 'Enable'}
                  </button>
                  <button className='btn btn-sm btn-outline-danger' disabled={deleting} onClick={() => onDelete(deal)}>
                    Delete
                  </button>
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

export default DealList;
