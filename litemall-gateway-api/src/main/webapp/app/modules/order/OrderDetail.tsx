import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link, useNavigate, useParams } from 'react-router-dom';

import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { isMissingEndpoint, orderApi } from 'app/shared/api';
import { IOrderDetail } from 'app/shared/model/order/order.model';
import './order.scss';

/**
 * Single-order view, modelled on litemall-vue `order/order-detail`: shipping
 * recipient, line items, and the price breakdown (goods / freight / coupon /
 * actual). Sourced from `/srv/order/detail`. Graceful when not live yet.
 */
const OrderDetailView: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [order, setOrder] = useState<IOrderDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [missing, setMissing] = useState(false);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    setLoading(true);
    orderApi
      .detail(id)
      .then(d => {
        if (!cancelled) setOrder(d ?? null);
      })
      .catch(e => {
        if (cancelled) return;
        if (isMissingEndpoint(e)) setMissing(true);
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (loading) {
    return (
      <div className='text-center my-5'>
        <Spinner animation='border' />
      </div>
    );
  }

  if (missing || !order) {
    return (
      <div className='container my-5 text-center text-muted'>
        <p>Order details aren’t available right now.</p>
        <Link to='/orders' className='btn btn-outline-primary btn-sm'>
          Back to my orders
        </Link>
      </div>
    );
  }

  return (
    <div className='container my-4 lm-orders' style={{ maxWidth: 760 }}>
      <button type='button' className='btn btn-link px-0 mb-2' onClick={() => navigate('/orders')}>
        <i className='bi bi-chevron-left' /> Back to my orders
      </button>

      <div className='lm-order-card'>
        <div className='lm-order-card__head'>
          <span className='text-muted small'>#{order.orderSn ?? order.id}</span>
          <span className='lm-order-card__status'>{order.orderStatusText}</span>
        </div>

        <div className='lm-order-detail__ship'>
          <i className='bi bi-geo-alt' /> <strong>{order.consignee}</strong> <span className='text-muted'>{order.mobile}</span>
          <div className='text-muted'>{order.address}</div>
        </div>

        <div className='lm-order-card__goods'>
          {(order.orderGoods ?? []).map(g => (
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
        </div>

        <ul className='lm-order-detail__totals'>
          <li>
            <span>Goods</span>
            <span>${priceNum(order.goodsPrice).toFixed(2)}</span>
          </li>
          <li>
            <span>Freight</span>
            <span>${priceNum(order.freightPrice).toFixed(2)}</span>
          </li>
          {priceNum(order.couponPrice) > 0 && (
            <li className='text-success'>
              <span>Coupon</span>
              <span>−${priceNum(order.couponPrice).toFixed(2)}</span>
            </li>
          )}
          <li className='lm-order-detail__grand'>
            <span>Actual paid</span>
            <strong>${priceNum(order.actualPrice).toFixed(2)}</strong>
          </li>
        </ul>

        {order.addTime && <div className='text-muted small mt-2'>Placed {order.addTime}</div>}
      </div>
    </div>
  );
};

export default OrderDetailView;
