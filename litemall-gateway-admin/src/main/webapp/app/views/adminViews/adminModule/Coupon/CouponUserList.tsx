import { promotionOpMessage, useGrantCouponMutation, useListCouponUsersQuery } from 'app/shared/reducers/private/services/adminPromotionApi';
import { PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link, useParams } from 'react-router-dom';

// Issue records of one coupon (who holds it, in what state) plus a
// direct-grant control, authenticated admin → promotion-service
// /srv/private/admin/promotion/coupon/{id}/users and .../grant.

const STATUS_TAG: Record<number, { tag: 'success' | 'info' | 'warning' | 'danger'; text: string }> = {
  0: { tag: 'success', text: 'usable' },
  1: { tag: 'info', text: 'used' },
  2: { tag: 'warning', text: 'expired' },
  3: { tag: 'danger', text: 'withdrawn' },
};

const CouponUserList: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const couponId = id ? Number(id) : undefined;
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [grantUserId, setGrantUserId] = React.useState('');
  const [grantMsg, setGrantMsg] = React.useState<{ ok: boolean; text: string } | null>(null);

  const { data, isLoading, isFetching, isError, error } = useListCouponUsersQuery({ page, limit, couponId: couponId as number }, { skip: couponId == null });
  const [grantCoupon, { isLoading: granting }] = useGrantCouponMutation();

  const list = data?.list ?? [];
  const errStatus = (error as { status?: number | string })?.status;

  const onGrant = async (e: React.FormEvent) => {
    e.preventDefault();
    setGrantMsg(null);
    const userId = Number(grantUserId);
    if (!userId || couponId == null) {
      setGrantMsg({ ok: false, text: 'Enter a numeric user ID.' });
      return;
    }
    const res = await grantCoupon({ couponId, userId });
    const msg = promotionOpMessage(res);
    setGrantMsg(msg ? { ok: false, text: msg } : { ok: true, text: `Coupon granted to user #${userId}.` });
    if (!msg) setGrantUserId('');
  };

  return (
    <div className='app-container'>
      <div className='filter-container'>
        <Link className='btn btn-outline-secondary filter-item' to='/admin/promotion/coupon'>
          ‹ Back to coupons
        </Link>
        <span className='filter-item text-muted'>Issued instances of coupon #{couponId}</span>
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
        <form className='d-flex filter-item' onSubmit={onGrant}>
          <input
            className='form-control me-1'
            style={{ width: 140 }}
            type='number'
            placeholder='User ID'
            value={grantUserId}
            onChange={e => setGrantUserId(e.target.value)}
            aria-label='User ID to grant to'
          />
          <button className='btn btn-primary' type='submit' disabled={granting}>
            {granting ? 'Granting…' : 'Grant'}
          </button>
        </form>
        {isFetching && <Spinner />}
      </div>

      {isError && <div className='alert alert-danger'>Failed to load issued coupons{errStatus ? ` (${errStatus})` : ''}.</div>}
      {grantMsg && <div className={`alert ${grantMsg.ok ? 'alert-success' : 'alert-danger'}`}>{grantMsg.text}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>#</th>
            <th className='text-end'>User ID</th>
            <th>Status</th>
            <th>Valid from</th>
            <th>Valid to</th>
            <th>Used at</th>
            <th className='text-end'>Order</th>
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
                No issued coupons.
              </td>
            </tr>
          ) : (
            list.map(cu => {
              const st = STATUS_TAG[cu.status ?? 0] ?? STATUS_TAG[0];
              return (
                <tr key={cu.id}>
                  <td>{cu.id}</td>
                  <td className='text-end'>{cu.userId}</td>
                  <td>
                    <Tag tag={st.tag}>{st.text}</Tag>
                  </td>
                  <td className='text-muted small'>{cu.startTime?.replace('T', ' ').slice(0, 16) ?? '—'}</td>
                  <td className='text-muted small'>{cu.endTime?.replace('T', ' ').slice(0, 16) ?? '—'}</td>
                  <td className='text-muted small'>{cu.usedTime?.replace('T', ' ').slice(0, 16) ?? '—'}</td>
                  <td className='text-end'>{cu.orderId ?? '—'}</td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      <Pagination page={page} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />
    </div>
  );
};

export default CouponUserList;
