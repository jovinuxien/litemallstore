import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Spinner } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';

import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { EmptyState, GoodsLineCard, Page, PageHead } from 'app/components/commonComponents/storefront';
import { actionErrorMessage, orderApi } from 'app/shared/api';
import { useTranslation } from 'app/i18n';
import { IOrderListItem } from 'app/shared/model/order/order.model';
import { money } from 'app/shared/util/money';
import './order.scss';

/**
 * Which orders belong on the Refunds page: an after-sales case, a paid order the
 * customer could still ask a refund for, or — the case this page used to HIDE — a
 * refund request already in progress (REFUND_REQUEST, 202). The customer payload has
 * no numeric status; `handleOption.withdrawRefund` is true exactly for 202 (order
 * lifecycle contract §3), so it is the key.
 */
export const isRefundRow = (o: IOrderListItem): boolean =>
  (o.aftersaleStatus ?? 0) > 0 || !!o.handleOption?.refund || !!o.handleOption?.withdrawRefund;

/**
 * After-sales / refunds, modelled on litemall-vue `user/refund-list`. There is
 * no dedicated `/srv` refund endpoint, so this lists orders filtered client-side
 * from `/srv/order/list`. A 202 order offers "Withdraw refund request" right here
 * (lifecycle D4); a refusal shows the server's reason verbatim.
 */
const RefundList: React.FC = () => {
  const { t } = useTranslation('order');
  const navigate = useNavigate();
  const [orders, setOrders] = useState<IOrderListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [actionError, setActionError] = useState<{ orderId?: number; message: string } | null>(null);

  const load = useCallback(async () => {
    try {
      const res = await orderApi.list({ showType: 0, page: 1, limit: 50 });
      setOrders((res?.list ?? []).filter(isRefundRow));
    } catch {
      setOrders([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const withdraw = async (orderId: number) => {
    setPending(true);
    setActionError(null);
    try {
      await orderApi.withdrawRefund(orderId);
      await load();
    } catch (e) {
      setActionError({ orderId, message: actionErrorMessage(e) });
    } finally {
      setPending(false);
    }
  };

  return (
    <Page>
      <PageHead title={t('refunds.title')} />
      <div className='container lm-orders'>
        {loading ? (
          <div className='text-center my-5'>
            <Spinner animation='border' />
          </div>
        ) : orders.length === 0 ? (
          <EmptyState icon='bi-arrow-counterclockwise' text={t('refunds.empty')} />
        ) : (
          orders.map(o => (
            <div key={o.id} className='lm-order-panel'>
              <div className='lm-order-panel__head'>
                <span className='lm-order-panel__sn'>#{o.orderSn ?? o.id}</span>
                <span className='lm-order-panel__status'>{o.orderStatusText}</span>
              </div>
              <div role='button' tabIndex={0} onClick={() => navigate(`/order/${o.id}`)} onKeyDown={e => e.key === 'Enter' && navigate(`/order/${o.id}`)}>
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
              {actionError && actionError.orderId === o.id && (
                <Alert variant='danger' className='mx-3 my-2 py-2 small' role='alert'>
                  {actionError.message}
                </Alert>
              )}
              <div className='lm-order-panel__foot'>
                <span className='lm-amount'>{t('list.total', { amount: money(priceNum(o.actualPrice)) })}</span>
                <div className='lm-order-panel__actions'>
                  {o.handleOption?.withdrawRefund && o.id != null && (
                    <button type='button' className='btn btn-sm btn-lm-outline' disabled={pending} onClick={() => withdraw(o.id as number)}>
                      {t('actions.withdrawRefund')}
                    </button>
                  )}
                  <button type='button' className='btn btn-sm btn-lm-outline' onClick={() => navigate(`/order/${o.id}`)}>
                    {t('actions.viewOrder')}
                  </button>
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </Page>
  );
};

export default RefundList;
