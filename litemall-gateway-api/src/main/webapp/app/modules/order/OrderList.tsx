import React, { useCallback, useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';

import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { EmptyState, GoodsLineCard, Page, PageHead, StatusTabs } from 'app/components/commonComponents/storefront';
import { isMissingEndpoint, orderApi } from 'app/shared/api';
import { IOrderListItem } from 'app/shared/model/order/order.model';
import './order.scss';

/** litemall-vue order-list showType tabs (index === showType 0–4). */
const TABS = ['All', 'Unpaid', 'Unshipped', 'Unreceived', 'Unrated'];

/**
 * Customer order history, modelled on litemall-vue `user/order-list`: status
 * tabs, per-order item thumbnails, status text, total, and contextual actions
 * (pay / cancel / confirm receipt / refund / delete / rebuy). Sourced from
 * `/srv/order/list`. When that endpoint isn't live yet it renders an empty state
 * rather than an error (follow-up: order worktree).
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
    <Page>
      <PageHead title='My Orders' />
      <div className='container lm-orders'>
        <StatusTabs tabs={TABS} active={showType} onChange={setShowType} />

        {loading ? (
          <div className='text-center my-5'>
            <Spinner animation='border' />
          </div>
        ) : orders.length === 0 ? (
          <EmptyState icon='bi-bag' text='You have no orders here yet.'>
            <Link to='/search' className='btn btn-lm-primary btn-sm'>
              Start shopping
            </Link>
          </EmptyState>
        ) : (
          orders.map(o => (
            <div key={o.id} className='lm-order-panel'>
              <div className='lm-order-panel__head'>
                <span className='lm-order-panel__sn'>
                  #{o.orderSn ?? o.id}
                  {o.source === 'cj' && <span className='badge bg-lm-primary ms-2'>Dropship</span>}
                </span>
                <span className='lm-order-panel__status'>{o.orderStatusText}</span>
              </div>
              <div role='button' tabIndex={0} onClick={() => navigate(`/order/${o.id}`)}>
                {(o.goodsList ?? []).map(g => (
                  <GoodsLineCard
                    key={g.id}
                    picUrl={g.picUrl}
                    name={g.goodsName}
                    specs={g.specifications}
                    price={priceNum(g.price)}
                    qty={g.number}
                  />
                ))}
              </div>
              <div className='lm-order-panel__foot'>
                <span className='lm-amount'>Total: ${priceNum(o.actualPrice).toFixed(2)}</span>
                <div className='lm-order-panel__actions'>
                  {o.handleOption?.pay && (
                    <button type='button' className='btn btn-sm btn-lm-primary' disabled={pending} onClick={() => navigate(`/pay/${o.id}`)}>
                      Pay now
                    </button>
                  )}
                  {o.handleOption?.cancel && o.id != null && (
                    <button type='button' className='btn btn-sm btn-lm-outline' disabled={pending} onClick={() => act(() => orderApi.cancel(o.id as number))}>
                      Cancel
                    </button>
                  )}
                  {o.handleOption?.confirm && o.id != null && (
                    <button type='button' className='btn btn-sm btn-lm-outline' disabled={pending} onClick={() => act(() => orderApi.confirm(o.id as number))}>
                      Confirm receipt
                    </button>
                  )}
                  {o.handleOption?.refund && o.id != null && (
                    <button type='button' className='btn btn-sm btn-lm-outline' disabled={pending} onClick={() => act(() => orderApi.refund(o.id as number))}>
                      Refund
                    </button>
                  )}
                  {o.handleOption?.delete && o.id != null && (
                    <button type='button' className='btn btn-sm btn-lm-outline' disabled={pending} onClick={() => act(() => orderApi.remove(o.id as number))}>
                      Delete
                    </button>
                  )}
                  {o.handleOption?.rebuy && (
                    <button type='button' className='btn btn-sm btn-lm-outline' disabled={pending} onClick={() => navigate(`/order/${o.id}`)}>
                      Buy again
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </Page>
  );
};

export default OrderList;
