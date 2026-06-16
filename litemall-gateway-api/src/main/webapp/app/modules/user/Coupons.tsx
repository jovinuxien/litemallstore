import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';

import { ICoupon, isMissingEndpoint, userApi } from 'app/shared/api';
import './user.scss';

/** litemall-vue coupon-list status tabs: 0 unused, 1 used, 2 expired. */
const TABS = [
  { key: 0, label: 'Available' },
  { key: 1, label: 'Used' },
  { key: 2, label: 'Expired' },
];

/**
 * My coupons, modelled on litemall-vue `user/coupon-list`. Sourced from
 * `/srv/coupon/mylist`; graceful empty when not live.
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

  return (
    <div className='container my-4 lm-user' style={{ maxWidth: 640 }}>
      <h1 className='h4 mb-3'>My coupons</h1>
      <div className='lm-orders__tabs mb-3'>
        {TABS.map(t => (
          <button key={t.key} type='button' className={`lm-orders__tab${status === t.key ? ' is-active' : ''}`} onClick={() => setStatus(t.key)}>
            {t.label}
          </button>
        ))}
      </div>

      {loading ? (
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      ) : coupons.length === 0 ? (
        <p className='text-muted text-center my-5'>No coupons in this category.</p>
      ) : (
        <div className='d-grid gap-2'>
          {coupons.map(c => (
            <div key={c.id} className='lm-coupon-row'>
              <div className='lm-coupon-row__val'>
                <span className='lm-coupon-row__amt'>${c.discount}</span>
                <span className='lm-coupon-row__min'>{c.min ? `over $${c.min}` : 'no minimum'}</span>
              </div>
              <div className='lm-coupon-row__info'>
                <div className='fw-semibold'>{c.name}</div>
                {c.desc && <div className='text-muted small'>{c.desc}</div>}
                {(c.startTime || c.endTime) && <div className='text-muted small'>{[c.startTime, c.endTime].filter(Boolean).join(' – ')}</div>}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default Coupons;
