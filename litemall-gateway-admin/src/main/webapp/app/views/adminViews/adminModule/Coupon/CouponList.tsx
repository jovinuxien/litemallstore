import { ICoupon } from 'app/shared/model/admin/promotion-system.model';
import { useDeleteCouponMutation, useListCouponsQuery } from 'app/shared/reducers/private/services/adminPromotionApi';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Coupon list with create/edit/delete + a link to the per-coupon issued list,
// authenticated admin → /srv/private/admin/coupon.

const TYPE_LABEL: Record<number, string> = { 0: 'general', 1: 'on register', 2: 'exchange code' };
const STATUS_TAG: Record<number, { tag: 'success' | 'warning' | 'info'; text: string }> = {
  0: { tag: 'success', text: 'normal' },
  1: { tag: 'info', text: 'expired' },
  2: { tag: 'warning', text: 'used up' },
};

const CouponList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [order, setOrder] = React.useState<'asc' | 'desc'>('desc');
  const [nameInput, setNameInput] = React.useState('');
  const [name, setName] = React.useState('');

  const { data, isLoading, isFetching, isError, error } = useListCouponsQuery({ page, limit, sort: 'add_time', order, name });
  const [deleteCoupon, { isLoading: deleting }] = useDeleteCouponMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setName(nameInput.trim());
  };

  const onDelete = async (c: ICoupon) => {
    if (!window.confirm(`Delete coupon "${c.name ?? c.id}"?`)) return;
    setActionError(null);
    const res = await deleteCoupon({ id: c.id });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  return (
    <div className='app-container'>
      <form className='filter-container' onSubmit={onSearch}>
        <input className='form-control filter-item' style={{ width: 200 }} placeholder='Coupon name' value={nameInput} onChange={e => setNameInput(e.target.value)} />
        <select className='form-select filter-item' style={{ width: 120 }} value={order} onChange={e => setOrder(e.target.value as 'asc' | 'desc')} aria-label='Sort direction'>
          <option value='desc'>Newest</option>
          <option value='asc'>Oldest</option>
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
        <button className='btn btn-primary filter-item' type='submit'>
          Search
        </button>
        <Link className='btn btn-success filter-item' to='/admin/promotion/coupon/create'>
          + New coupon
        </Link>
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load coupons{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Name</th>
            <th>Type</th>
            <th className='text-end'>Discount</th>
            <th className='text-end'>Min spend</th>
            <th className='text-end'>Total</th>
            <th>Status</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={7} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={7} className='text-center text-muted py-5'>
                No coupons found.
              </td>
            </tr>
          ) : (
            list.map(c => {
              const st = STATUS_TAG[c.status ?? 0] ?? STATUS_TAG[0];
              return (
                <tr key={c.id}>
                  <td>
                    <Link to={`/admin/promotion/coupon/${c.id}`}>{c.name || `#${c.id}`}</Link>
                    {c.code && <span className='text-muted small ms-1'>({c.code})</span>}
                  </td>
                  <td>{TYPE_LABEL[c.type ?? 0] ?? c.type}</td>
                  <td className='text-end'>{c.discount != null ? `-${c.discount}` : '—'}</td>
                  <td className='text-end'>{c.min ?? '—'}</td>
                  <td className='text-end'>{c.total ?? '—'}</td>
                  <td>
                    <Tag tag={st.tag}>{st.text}</Tag>
                  </td>
                  <td className='text-end'>
                    <Link to={`/admin/promotion/coupon/${c.id}/issued`} className='btn btn-sm btn-outline-secondary me-1'>
                      Issued
                    </Link>
                    <Link to={`/admin/promotion/coupon/${c.id}`} className='btn btn-sm btn-outline-primary me-1'>
                      Edit
                    </Link>
                    <button className='btn btn-sm btn-outline-danger' disabled={deleting} onClick={() => onDelete(c)}>
                      Delete
                    </button>
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={pages} total={total} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />
    </div>
  );
};

export default CouponList;
