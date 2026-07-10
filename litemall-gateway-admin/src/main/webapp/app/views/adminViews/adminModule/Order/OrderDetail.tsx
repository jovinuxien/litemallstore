import { orderStatusInfo } from 'app/shared/model/admin/order.model';
import { useReadOrderQuery } from 'app/shared/reducers/private/services/adminCatalogApi';
import { ITracking, useGetTrackingQuery } from 'app/shared/reducers/private/services/adminOrderCjApi';
import { Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Read-only admin order detail — order summary, customer, shipping, line
// items and the logistics/tracking panel (order's CJ trackInfo surface).
// Fetched per id from order-service (/srv/private/admin/order/detail) as an
// authenticated admin via adminCatalogApi.

const money = (v?: number | string): string => `¥${Number(v ?? 0).toFixed(2)}`;

const UNAVAILABLE: ITracking = { available: false, shipped: false, events: [] };

// Logistics / tracking card. Degrades in layers: endpoint missing/erroring →
// muted note (order's tracking surface is a Wave-3 dependency, see
// docs/handoff-order-admin-cj.md); live but not shipped → clean empty state;
// shipped → carrier + tracking number + event list.
const TrackingPanel: React.FC<{ orderId: string }> = ({ orderId }) => {
  const { data, isLoading, isError } = useGetTrackingQuery(orderId);
  const tracking = isError ? UNAVAILABLE : data;

  return (
    <div className='box-card mt-3'>
      <h6>Logistics / Tracking</h6>
      {isLoading ? (
        <span className='spinner-border spinner-border-sm text-primary' role='status' />
      ) : !tracking || !tracking.available ? (
        <div className='text-muted small'>Tracking not available — the order service does not expose its tracking endpoint yet.</div>
      ) : !tracking.shipped ? (
        <div className='text-muted'>Not shipped yet — no tracking events.</div>
      ) : (
        <>
          <div className='small mb-2'>
            {tracking.carrier && (
              <span className='me-3'>
                Carrier: <strong>{tracking.carrier}</strong>
              </span>
            )}
            {tracking.trackNumber && (
              <span className='me-3'>
                Tracking #: <strong>{tracking.trackNumber}</strong>
              </span>
            )}
            {tracking.cjOrderStatus && <Tag tag='primary'>{tracking.cjOrderStatus}</Tag>}
          </div>
          {tracking.events.length === 0 ? (
            <div className='text-muted small'>No tracking events reported yet.</div>
          ) : (
            <table className='el-table'>
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Status</th>
                  <th>Detail</th>
                  <th>Location</th>
                </tr>
              </thead>
              <tbody>
                {tracking.events.map((ev, i) => (
                  <tr key={i}>
                    <td className='text-muted small'>{ev.time ?? '—'}</td>
                    <td>{ev.status ?? '—'}</td>
                    <td>{ev.description ?? '—'}</td>
                    <td className='text-muted small'>{ev.location ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </div>
  );
};

const OrderDetail: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const { data, isLoading, isError, error } = useReadOrderQuery(id as string, { skip: id == null });

  if (isLoading) {
    return (
      <div className='app-container text-center p-5'>
        <span className='spinner-border text-primary' role='status' />
      </div>
    );
  }

  const errStatus = (error as { status?: number | string })?.status;
  const order = data?.order;
  if (isError || !order) {
    return (
      <div className='app-container'>
        <div className='alert alert-danger'>Failed to load order{errStatus ? ` (${errStatus})` : ''}.</div>
        <button className='btn btn-outline-secondary' onClick={() => navigate(-1)}>
          ‹ Back
        </button>
      </div>
    );
  }

  const st = orderStatusInfo(order.orderStatus);
  const goods = data?.orderGoods ?? [];
  const user = data?.user;

  return (
    <div className='app-container'>
      <div className='d-flex align-items-center justify-content-between mb-3'>
        <h4 className='m-0'>
          Order {order.orderSn || `#${order.id}`} <Tag tag={st.tag}>{order.orderStatusText || st.label}</Tag>
          {order.source === 'cj' && (
            <span className='ms-2'>
              <Tag tag='warning'>CJ dropship{order.cjOrderNum ? ` · ${order.cjOrderNum}` : ''}</Tag>
            </span>
          )}
        </h4>
        <button className='btn btn-outline-secondary btn-sm' onClick={() => navigate('/admin/mall/order')}>
          ‹ Back to orders
        </button>
      </div>

      <div className='row g-3'>
        <div className='col-md-6'>
          <div className='box-card'>
            <h6>Customer</h6>
            <div>{user?.nickname || order.userName || `#${order.userId ?? '—'}`}</div>
            {user?.mobile && <div className='text-muted small'>{user.mobile}</div>}
          </div>
        </div>
        <div className='col-md-6'>
          <div className='box-card'>
            <h6>Shipping</h6>
            <div>{order.consignee || '—'}</div>
            {order.mobile && <div className='text-muted small'>{order.mobile}</div>}
            {order.address && <div className='text-muted small'>{order.address}</div>}
            {order.shipChannel && (
              <div className='small mt-1'>
                {order.shipChannel} · {order.shipSn}
              </div>
            )}
          </div>
        </div>
      </div>

      <div className='box-card mt-3'>
        <h6>Items</h6>
        <table className='el-table'>
          <thead>
            <tr>
              <th>Item</th>
              <th>Spec</th>
              <th className='text-end'>Price</th>
              <th className='text-end'>Qty</th>
            </tr>
          </thead>
          <tbody>
            {goods.length === 0 ? (
              <tr>
                <td colSpan={4} className='text-center text-muted py-4'>
                  No items.
                </td>
              </tr>
            ) : (
              goods.map(g => (
                <tr key={g.id}>
                  <td>
                    <div className='d-flex align-items-center gap-2'>
                      {g.picUrl && <img src={g.picUrl} alt='' className='cell-thumb' />}
                      <span>{g.goodsName}</span>
                    </div>
                  </td>
                  <td className='text-muted small'>{(g.specifications ?? []).join(' / ') || '—'}</td>
                  <td className='text-end'>{money(g.price)}</td>
                  <td className='text-end'>{g.number ?? '—'}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className='box-card mt-3'>
        <h6>Totals</h6>
        <div className='d-flex justify-content-between'>
          <span className='text-muted'>Goods</span>
          <span>{money(order.orderPrice)}</span>
        </div>
        <div className='d-flex justify-content-between'>
          <span className='text-muted'>Freight</span>
          <span>{money(order.freightPrice)}</span>
        </div>
        <div className='d-flex justify-content-between fw-bold mt-1'>
          <span>Paid</span>
          <span>{money(order.actualPrice)}</span>
        </div>
        {order.payTime && <div className='text-muted small mt-1'>Paid at {order.payTime}</div>}
      </div>

      {id && <TrackingPanel orderId={id} />}
    </div>
  );
};

export default OrderDetail;
