import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { isMissingEndpoint, orderApi } from 'app/shared/api';
import { IOrderListItem } from 'app/shared/model/order/order.model';
import './order.scss';

/**
 * After-sales / refunds, modelled on litemall-vue `user/refund-list`. There is
 * no dedicated `/srv` refund endpoint yet, so this lists orders flagged with an
 * after-sale status (filtered client-side from `/srv/order/list`). Graceful
 * empty when the order list endpoint isn't live.
 */
const RefundList: React.FC = () => {
  const [orders, setOrders] = useState<IOrderListItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    orderApi
      .list({ showType: 0, page: 1, limit: 50 })
      .then(res => {
        if (cancelled) return;
        const refunds = (res?.list ?? []).filter(o => (o.aftersaleStatus ?? 0) > 0 || o.handleOption?.refund);
        setOrders(refunds);
      })
      .catch(e => {
        if (!cancelled && !isMissingEndpoint(e)) setOrders([]);
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className='container my-4 lm-orders' style={{ maxWidth: 760 }}>
      <h1 className='h4 mb-3'>After-sales & refunds</h1>
      {loading ? (
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      ) : orders.length === 0 ? (
        <p className='text-muted text-center my-5'>No refund or after-sales requests.</p>
      ) : (
        <div className='d-grid gap-3'>
          {orders.map(o => (
            <Link key={o.id} to={`/order/${o.id}`} className='lm-order-card text-decoration-none text-reset'>
              <div className='lm-order-card__head'>
                <span className='text-muted small'>#{o.orderSn ?? o.id}</span>
                <span className='lm-order-card__status'>{o.orderStatusText}</span>
              </div>
              <div className='lm-order-card__foot'>
                <span>
                  Total: <strong>${priceNum(o.actualPrice).toFixed(2)}</strong>
                </span>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
};

export default RefundList;
