import { ICoupon } from 'app/shared/model/admin/promotion-system.model';
import { promotionOpMessage, useDeleteCouponMutation, useListCouponsQuery } from 'app/shared/reducers/private/services/adminPromotionApi';
import { PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import DeliverCouponDialog from './DeliverCouponDialog';
import { couponScopeLabel, discountTypeLabel, fmtCouponDiscount } from './couponFormat';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Coupon list with create/edit/delete + a link to the per-coupon issued list,
// authenticated admin → promotion-service /srv/private/admin/promotion/coupon.
// The list endpoint is page/limit only (no name filter or sort) and returns a
// bare array, so the pager runs on the has-more heuristic.
// Wave 18: discount column renders flat vs percent (incl. cap) and new
// discount-type + scope (All/Category/Products) columns.
// Wave 22: "Deliver" opens the RFM segment-delivery dialog (preview-first).

const TYPE_LABEL: Record<number, string> = { 0: 'general', 1: 'on register', 2: 'exchange code' };
const STATUS_TAG: Record<number, { tag: 'success' | 'warning' | 'info'; text: string }> = {
  0: { tag: 'success', text: 'normal' },
  1: { tag: 'info', text: 'expired' },
  2: { tag: 'warning', text: 'used up' },
};

const CouponList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);

  const { data, isLoading, isFetching, isError, error } = useListCouponsQuery({ page, limit });
  const [deleteCoupon, { isLoading: deleting }] = useDeleteCouponMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);
  // Wave 22: coupon whose segment-delivery dialog is open.
  const [delivering, setDelivering] = React.useState<ICoupon | null>(null);

  const list = data?.list ?? [];
  const errStatus = (error as { status?: number | string })?.status;

  const onDelete = async (c: ICoupon) => {
    if (!window.confirm(`Delete coupon "${c.name ?? c.id}"?`)) return;
    setActionError(null);
    const res = await deleteCoupon(c);
    setActionError(promotionOpMessage(res));
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
        <Link className='btn btn-success filter-item' to='/admin/promotion/coupon/create'>
          + New coupon
        </Link>
        {isFetching && <Spinner />}
      </div>

      {isError && <div className='alert alert-danger'>Failed to load coupons{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Discount type</th>
            <th className='text-end'>Discount</th>
            <th>Scope</th>
            <th className='text-end'>Min spend</th>
            <th className='text-end'>Total</th>
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
                  <td>
                    <Tag tag={discountTypeLabel(c) === 'percent' ? 'primary' : 'info'}>{discountTypeLabel(c)}</Tag>
                  </td>
                  <td className='text-end'>{fmtCouponDiscount(c)}</td>
                  <td>{couponScopeLabel(c)}</td>
                  <td className='text-end'>{c.min ?? '—'}</td>
                  <td className='text-end'>{c.total ?? '—'}</td>
                  <td>
                    <Tag tag={st.tag}>{st.text}</Tag>
                  </td>
                  <td className='text-end'>
                    <button className='btn btn-sm btn-outline-success me-1' onClick={() => setDelivering(c)}>
                      Deliver
                    </button>
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

      <Pagination page={page} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />

      {delivering && <DeliverCouponDialog coupon={delivering} onClose={() => setDelivering(null)} />}
    </div>
  );
};

export default CouponList;
