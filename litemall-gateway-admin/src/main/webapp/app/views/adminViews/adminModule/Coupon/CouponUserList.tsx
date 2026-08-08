import {
  promotionOpMessage,
  useGetCouponPerformanceQuery,
  useGrantCouponMutation,
  useListCouponDeliveriesQuery,
  useListCouponUsersQuery,
  useReadCouponQuery,
} from 'app/shared/reducers/private/services/adminPromotionApi';
import { PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import { fmtInt, fmtMoney, fmtPct } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import DeliverCouponDialog from './DeliverCouponDialog';
import { deliverySegmentLabel } from './couponDeliveryFormat';
import * as React from 'react';
import { Link, useParams } from 'react-router-dom';

// Issue records of one coupon (who holds it, in what state) plus a
// direct-grant control, authenticated admin → promotion-service
// /srv/private/admin/promotion/coupon/{id}/users and .../grant.
// Wave 22: performance card (granted/used/redemption/orders/revenue/AOV over
// litemall_coupon_user + litemall_order, read-only), segment-delivery dialog
// and the deliveries history table (.../coupon/deliveries?couponId=).

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

  // Wave 22: coupon read (for the dialog title), performance + deliveries.
  const { data: coupon } = useReadCouponQuery(id as string, { skip: couponId == null });
  const { data: perf, isError: perfError } = useGetCouponPerformanceQuery(couponId as number, { skip: couponId == null });
  const [deliveryPage, setDeliveryPage] = React.useState(1);
  const { data: deliveries, isFetching: deliveriesFetching } = useListCouponDeliveriesQuery(
    { couponId: couponId as number, page: deliveryPage, limit: 10 },
    { skip: couponId == null }
  );
  const [delivering, setDelivering] = React.useState(false);

  const list = data?.list ?? [];
  const deliveryRows = deliveries?.list ?? [];
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
        <button className='btn btn-success filter-item' type='button' onClick={() => setDelivering(true)}>
          Deliver to segment
        </button>
        {isFetching && <Spinner />}
      </div>

      {isError && <div className='alert alert-danger'>Failed to load issued coupons{errStatus ? ` (${errStatus})` : ''}.</div>}
      {grantMsg && <div className={`alert ${grantMsg.ok ? 'alert-success' : 'alert-danger'}`}>{grantMsg.text}</div>}

      {/* Wave 22: performance over issued coupons + the orders they discounted. */}
      {!perfError && perf && (
        <div className='card mb-3'>
          <div className='card-body py-3'>
            <h6 className='card-title mb-2'>Performance</h6>
            <div className='row text-center'>
              {(
                [
                  ['Granted', fmtInt(perf.granted)],
                  ['Used', fmtInt(perf.used)],
                  ['Redemption', fmtPct(perf.redemptionPct)],
                  ['Orders', fmtInt(perf.ordersCount)],
                  ['Revenue', fmtMoney(perf.revenue)],
                  ['Avg order value', fmtMoney(perf.avgOrderValue)],
                ] as [string, string][]
              ).map(([label, value]) => (
                <div className='col-4 col-md-2' key={label}>
                  <div className='text-muted small'>{label}</div>
                  <div className='fw-semibold'>{value}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

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

      {/* Wave 22: segment-delivery history for this coupon. */}
      <h6 className='mt-4'>
        Segment deliveries
        {deliveriesFetching && <Spinner />}
      </h6>
      <table className='el-table'>
        <thead>
          <tr>
            <th>#</th>
            <th>Segment</th>
            <th className='text-end'>Matched</th>
            <th className='text-end'>Granted</th>
            <th className='text-end'>Skipped</th>
            <th>Delivered at</th>
          </tr>
        </thead>
        <tbody>
          {deliveryRows.length === 0 ? (
            <tr>
              <td colSpan={6} className='text-center text-muted py-4'>
                No segment deliveries yet.
              </td>
            </tr>
          ) : (
            deliveryRows.map((d, i) => (
              <tr key={d.id ?? i}>
                <td>{d.id ?? '—'}</td>
                <td>{deliverySegmentLabel(d)}</td>
                <td className='text-end'>{d.matched ?? '—'}</td>
                <td className='text-end'>{d.granted ?? '—'}</td>
                <td className='text-end'>{d.skipped ?? '—'}</td>
                <td className='text-muted small'>{d.addTime?.replace('T', ' ').slice(0, 16) ?? '—'}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>
      {(deliveryPage > 1 || deliveryRows.length >= 10) && (
        <Pagination
          page={deliveryPage}
          pages={deliveries?.pages}
          total={deliveries?.total || undefined}
          rowCount={deliveryRows.length}
          limit={10}
          busy={deliveriesFetching}
          onPage={setDeliveryPage}
        />
      )}

      {delivering && couponId != null && (
        <DeliverCouponDialog coupon={coupon?.id != null ? coupon : { id: couponId }} onClose={() => setDelivering(false)} />
      )}
    </div>
  );
};

export default CouponUserList;
