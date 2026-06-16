import React, { useCallback, useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';

import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { isMissingEndpoint, orderApi } from 'app/shared/api';
import { IOrderListItem } from 'app/shared/model/order/order.model';
import './order.scss';

/** litemall-vue order-list showType tabs. */
const TABS: { key: number; label: string }[] = [
  { key: 0, label: 'All' },
  { key: 1, label: 'Unpaid' },
  { key: 2, label: 'To ship' },
  { key: 3, label: 'Shipped' },
  { key: 4, label: 'To review' },
];

/**
 * Customer order history, modelled on litemall-vue `user/order-list`: status
 * tabs, per-order item thumbnails, status text, total, and contextual actions
 * (pay / cancel / confirm receipt / refund). Sourced from `/srv/order/list`.
 * When that endpoint isn't live yet it renders an empty state rather than an
 * error (follow-up: order worktree).
 */
const OrderList: React.FC = () => {
  const navigate = useNavigate();
  const [showType, setShowType] = useState(0);
  const [orders, setOrders] = useState<IOrderListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);

  const load = useCallback(async (type: number) => {
    setLoading(true);
    try {
      const res = await orderApi.list({ showType: type, page: 1, limit: 20 });
      setOrders(res?.list ?? []);
    } catch (e) {
      if (!isMissingEndpoint(e)) {
        // Real error: still show empty, but it's distinguishable in console.
        console.error('order list failed', e); // eslint-disable-line no-console
      }
      setOrders([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(showType);
  }, [load, showType]);

  const act = async (fn: () => Promise<unknown>) => {
    setPending(true);
    try {
      await fn();
      await load(showType);
    } catch {
      /* surfaced via reload; keep UI responsive */
    } finally {
      setPending(false);
    }
  };

  return (
    <div className='container my-4 lm-orders'>
      <h1 className='h4 mb-3'>My orders</h1>

      <div className='lm-orders__tabs'>
        {TABS.map(t => (
          <button key={t.key} type='button' className={`lm-orders__tab${showType === t.key ? ' is-active' : ''}`} onClick={() => setShowType(t.key)}>
            {t.label}
          </button>
        ))}
      </div>

      {loading ? (
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      ) : orders.length === 0 ? (
        <div className='text-center my-5 text-muted'>
          <p className='mb-2'>You have no orders here yet.</p>
          <Link to='/search' className='btn btn-outline-primary btn-sm'>
            Start shopping
          </Link>
        </div>
      ) : (
        <div className='d-grid gap-3'>
          {orders.map(o => (
            <div key={o.id} className='lm-order-card'>
              <div className='lm-order-card__head'>
                <span className='text-muted small'>#{o.orderSn ?? o.id}</span>
                <span className='lm-order-card__status'>{o.orderStatusText}</span>
              </div>
              <button type='button' className='lm-order-card__goods' onClick={() => navigate(`/order/${o.id}`)}>
                {(o.goodsList ?? []).map(g => (
                  <div key={g.id} className='lm-order-card__line'>
                    <img src={g.picUrl} alt={g.goodsName} />
                    <div className='lm-order-card__lineinfo'>
                      <div className='lm-order-card__name'>{g.goodsName}</div>
                      {g.specifications && g.specifications.length > 0 && <div className='text-muted small'>{g.specifications.join(' / ')}</div>}
                    </div>
                    <div className='lm-order-card__lineqty'>
                      <div>${priceNum(g.price).toFixed(2)}</div>
                      <div className='text-muted small'>×{g.number}</div>
                    </div>
                  </div>
                ))}
              </button>
              <div className='lm-order-card__foot'>
                <span>
                  Total: <strong>${priceNum(o.actualPrice).toFixed(2)}</strong>
                </span>
                <div className='lm-order-card__actions'>
                  <Link to={`/order/${o.id}`} className='btn btn-sm btn-outline-secondary'>
                    Details
                  </Link>
                  {o.handleOption?.pay && (
                    <button type='button' className='btn btn-sm btn-primary' disabled={pending} onClick={() => navigate(`/pay/${o.id}`)}>
                      Pay now
                    </button>
                  )}
                  {o.handleOption?.cancel && o.id != null && (
                    <button type='button' className='btn btn-sm btn-outline-danger' disabled={pending} onClick={() => act(() => orderApi.cancel(o.id as number))}>
                      Cancel
                    </button>
                  )}
                  {o.handleOption?.confirm && o.id != null && (
                    <button type='button' className='btn btn-sm btn-success' disabled={pending} onClick={() => act(() => orderApi.confirm(o.id as number))}>
                      Confirm receipt
                    </button>
                  )}
                  {o.handleOption?.refund && o.id != null && (
                    <button type='button' className='btn btn-sm btn-outline-warning' disabled={pending} onClick={() => act(() => orderApi.refund(o.id as number))}>
                      Refund
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default OrderList;
