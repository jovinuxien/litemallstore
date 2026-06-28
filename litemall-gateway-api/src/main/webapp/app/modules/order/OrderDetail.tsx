import React, { useCallback, useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link, useNavigate, useParams } from 'react-router-dom';

import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { Cell, CellGroup, EmptyState, GoodsLineCard, OrderSummary, Page, PageHead } from 'app/components/commonComponents/storefront';
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
  const [pending, setPending] = useState(false);

  const fetchDetail = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const d = await orderApi.detail(id);
      setOrder(d ?? null);
    } catch (e) {
      if (isMissingEndpoint(e)) setMissing(true);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchDetail();
  }, [fetchDetail]);

  // Run an order action (cancel/confirm/refund/delete) then refresh the detail.
  const act = async (fn: () => Promise<unknown>) => {
    setPending(true);
    try {
      await fn();
      await fetchDetail();
    } catch {
      /* surfaced via reload; keep UI responsive */
    } finally {
      setPending(false);
    }
  };

  if (loading) {
    return (
      <Page>
        <PageHead title='Order detail' />
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      </Page>
    );
  }

  if (missing || !order) {
    return (
      <Page>
        <PageHead title='Order detail' />
        <div className='container'>
          <CellGroup>
            <EmptyState icon='bi-receipt' text='Order details aren’t available right now.'>
              <Link to='/orders' className='btn btn-lm-outline'>
                Back to my orders
              </Link>
            </EmptyState>
          </CellGroup>
        </div>
      </Page>
    );
  }

  const opt = order.handleOption;
  const orderId = order.id;

  return (
    <Page>
      <PageHead title='Order detail' sub={order.orderStatusText} />
      <div className='container'>
        {/* Goods */}
        <CellGroup title='Items'>
          {(order.orderGoods ?? []).map(g => (
            <GoodsLineCard
              key={g.id}
              picUrl={g.picUrl}
              name={g.goodsName}
              to={g.goodsId ? `/product/${g.goodsId}` : undefined}
              specs={g.specifications}
              price={priceNum(g.price)}
              qty={g.number}
            />
          ))}
        </CellGroup>

        {/* Money */}
        <CellGroup>
          <OrderSummary
            rows={[
              { label: 'Goods total', value: `$${priceNum(order.goodsPrice).toFixed(2)}` },
              {
                label: 'Shipping',
                value: priceNum(order.freightPrice) > 0 ? `$${priceNum(order.freightPrice).toFixed(2)}` : 'Free',
                variant: 'muted',
              },
              ...(priceNum(order.couponPrice) > 0
                ? [{ label: 'Coupon', value: `−$${priceNum(order.couponPrice).toFixed(2)}`, variant: 'success' as const }]
                : []),
              { label: 'Total paid', value: `$${priceNum(order.actualPrice).toFixed(2)}`, variant: 'total' },
            ]}
          />
        </CellGroup>

        {/* Delivery address */}
        <CellGroup title='Delivery address'>
          <Cell title={`${order.consignee ?? ''}${order.mobile ? ` · ${order.mobile}` : ''}`}>
            <span className='text-muted'>{order.address}</span>
          </Cell>
        </CellGroup>

        {/* Order meta */}
        <CellGroup>
          {order.addTime && <Cell title='Order time' value={order.addTime} />}
          <Cell title='Order no.' value={order.orderSn ?? order.id} />
          {order.orderStatusText && <Cell title='Status' value={order.orderStatusText} />}
        </CellGroup>

        {/* Actions */}
        {opt && (opt.pay || opt.cancel || opt.confirm || opt.refund || opt.delete) && (
          <CellGroup>
            <div className='p-3 d-flex flex-wrap gap-2 justify-content-end'>
              {opt.pay && orderId != null && (
                <button type='button' className='btn btn-lm-primary btn-sm' disabled={pending} onClick={() => navigate(`/pay/${orderId}`)}>
                  Pay now
                </button>
              )}
              {opt.cancel && orderId != null && (
                <button type='button' className='btn btn-lm-outline btn-sm' disabled={pending} onClick={() => act(() => orderApi.cancel(orderId))}>
                  Cancel
                </button>
              )}
              {opt.confirm && orderId != null && (
                <button type='button' className='btn btn-lm-outline btn-sm' disabled={pending} onClick={() => act(() => orderApi.confirm(orderId))}>
                  Confirm receipt
                </button>
              )}
              {opt.refund && orderId != null && (
                <button type='button' className='btn btn-lm-outline btn-sm' disabled={pending} onClick={() => act(() => orderApi.refund(orderId))}>
                  Refund
                </button>
              )}
              {opt.delete && orderId != null && (
                <button
                  type='button'
                  className='btn btn-lm-outline btn-sm'
                  disabled={pending}
                  onClick={() => act(async () => {
                    await orderApi.remove(orderId);
                    navigate('/orders');
                  })}
                >
                  Delete
                </button>
              )}
            </div>
          </CellGroup>
        )}
      </div>
    </Page>
  );
};

export default OrderDetailView;
