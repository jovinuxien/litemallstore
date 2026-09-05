import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { EmptyState, GoodsLineCard, Page, PageHead } from 'app/components/commonComponents/storefront';
import { orderApi } from 'app/shared/api';
import { useTranslation } from 'app/i18n';
import { IOrderListItem } from 'app/shared/model/order/order.model';
import { money } from 'app/shared/util/money';
import './order.scss';

/**
 * After-sales / refunds, modelled on litemall-vue `user/refund-list`. There is
 * no dedicated `/srv` refund endpoint yet, so this lists orders flagged with an
 * after-sale status (filtered client-side from `/srv/order/list`). Graceful
 * empty when the order list endpoint isn't live.
 */
const RefundList: React.FC = () => {
  const { t } = useTranslation('order');
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
      .catch(() => {
        if (!cancelled) setOrders([]);
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, []);

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
            <Link key={o.id} to={`/order/${o.id}`} className='lm-order-panel d-block text-decoration-none text-reset'>
              <div className='lm-order-panel__head'>
                <span className='lm-order-panel__sn'>#{o.orderSn ?? o.id}</span>
                <span className='lm-order-panel__status'>{o.orderStatusText}</span>
              </div>
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
              <div className='lm-order-panel__foot'>
                <span className='lm-amount'>{t('list.total', { amount: money(priceNum(o.actualPrice)) })}</span>
              </div>
            </Link>
          ))
        )}
      </div>
    </Page>
  );
};

export default RefundList;
