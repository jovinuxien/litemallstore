import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';

import { ICoupon, isMissingEndpoint, userApi } from 'app/shared/api';
import { EmptyState, Page, PageHead, StatusTabs } from 'app/components/commonComponents/storefront';
import './user.scss';

/** litemall-vue coupon-list status tabs: 0 unused, 1 used, 2 expired. */
const TABS = ['Unused', 'Used', 'Expired'];

/**
 * My coupons, modelled on litemall-vue `user/coupon-list`. Sourced from
 * `/srv/coupon/mylist`; graceful empty when not live. The tab index IS the
 * status code the backend expects (0 unused / 1 used / 2 expired).
 */
const Coupons: React.FC = () => {
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
      .catch(e => {
        if (!cancelled && !isMissingEndpoint(e)) setCoupons([]);
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
      <PageHead title='My Coupons' />
      <div className='container'>
        <StatusTabs tabs={TABS} active={status} onChange={setStatus} />

        {loading ? (
          <div className='text-center my-5'>
            <Spinner animation='border' />
          </div>
        ) : coupons.length === 0 ? (
          <EmptyState icon='bi-ticket-perforated' text='No coupons here.' />
        ) : (
          <div className='d-grid gap-3'>
            {coupons.map(c => (
              <div key={c.id} className={`lm-coupon-card${disabled ? ' is-disabled' : ''}`}>
                <div className='lm-coupon-card__value'>
                  <div className='lm-coupon-card__amt'>${c.discount}</div>
                  <div className='lm-coupon-card__cond'>{c.min ? `Spend $${c.min}` : 'No minimum'}</div>
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
