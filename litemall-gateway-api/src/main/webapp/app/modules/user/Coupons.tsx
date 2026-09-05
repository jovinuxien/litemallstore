import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { ICoupon, userApi } from 'app/shared/api';
import { EmptyState, Page, PageHead, StatusTabs } from 'app/components/commonComponents/storefront';
import { Trans, useTranslation } from 'app/i18n';
import { couponConditionLabel, couponValueShort } from 'app/shared/util/couponFormat';
import './user.scss';

/** litemall-vue coupon-list status tabs: 0 unused, 1 used, 2 expired. */
/**
 * My coupons, modelled on litemall-vue `user/coupon-list`. Sourced from
 * `/srv/coupon/mylist`. The tab index IS the status code the backend
 * expects (0 unused / 1 used / 2 expired). Wave 18: percent coupons render
 * "N%" + their cap; the coupon center is linked for claiming more.
 */
const Coupons: React.FC = () => {
  const { t } = useTranslation('coupon');
  const tabs = [t('mine.unused'), t('mine.used'), t('mine.expired')];
  const [status, setStatus] = useState(0);
  const [coupons, setCoupons] = useState<ICoupon[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    userApi
      .couponMyList(status, { page: 1, limit: 30 })
      .then(res => {
        if (!cancelled) setCoupons(res?.list ?? []);
      })
      .catch(() => {
        if (!cancelled) setCoupons([]);
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [status]);

  // Used/expired coupons render greyed-out.
  const disabled = status !== 0;

  return (
    <Page>
      <PageHead
        title={t('mine.title')}
        sub={<Trans t={t} i18nKey='mine.sub' components={{ 1: <Link to='/coupons' /> }} />}
      />
      <div className='container'>
        <StatusTabs tabs={tabs} active={status} onChange={setStatus} />

        {loading ? (
          <div className='text-center my-5'>
            <Spinner animation='border' />
          </div>
        ) : coupons.length === 0 ? (
          <EmptyState icon='bi-ticket-perforated' text={t('mine.empty')}>
            <Link to='/coupons' className='btn btn-sm btn-outline-primary mt-2'>
              {t('mine.browse')}
            </Link>
          </EmptyState>
        ) : (
          <div className='d-grid gap-3'>
            {coupons.map(c => (
              <div key={c.id} className={`lm-coupon-card${disabled ? ' is-disabled' : ''}`}>
                <div className='lm-coupon-card__value'>
                  <div className='lm-coupon-card__amt'>{couponValueShort(c)}</div>
                  <div className='lm-coupon-card__cond'>{couponConditionLabel(c)}</div>
                </div>
                <div className='lm-coupon-card__body'>
                  <div className='lm-coupon-card__name'>{c.name}</div>
                  {(c.desc || c.tag) && <div className='lm-coupon-card__desc'>{c.desc || c.tag}</div>}
                  {(c.startTime || c.endTime) && (
                    <div className='lm-coupon-card__dates'>{[c.startTime, c.endTime].filter(Boolean).join(' – ')}</div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </Page>
  );
};

export default Coupons;
