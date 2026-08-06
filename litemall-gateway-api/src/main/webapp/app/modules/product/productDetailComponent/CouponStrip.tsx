import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { ICoupon, userApi } from 'app/shared/api';
import { couponValueShort, isPercentCoupon } from 'app/shared/util/couponFormat';

/**
 * Receivable-coupon strip on the detail page, mirroring litemall-vue's coupon
 * row. Reads public coupons (`/srv/coupon/list`) and lets the customer claim
 * one (`/srv/coupon/receive`). Renders nothing when no coupons are on offer.
 * Wave 18: percent coupons render "−N%"; "see all" links the coupon center.
 */
const CouponStrip: React.FC = () => {
  const [coupons, setCoupons] = useState<ICoupon[]>([]);
  const [claimed, setClaimed] = useState<Record<number, boolean>>({});

  useEffect(() => {
    let cancelled = false;
    userApi
      .couponList({ page: 1, limit: 4 })
      .then(res => {
        if (!cancelled) setCoupons(res?.list ?? []);
      })
      .catch(() => {
        if (!cancelled) setCoupons([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const claim = async (c: ICoupon) => {
    if (c.id == null || claimed[c.id]) return;
    setClaimed(prev => ({ ...prev, [c.id as number]: true }));
    try {
      await userApi.couponReceive(c.id);
    } catch {
      /* keep the optimistic claimed state; surfacing an error here is noisy */
    }
  };

  if (!coupons.length) return null;

  return (
    <div className='lm-pdp__coupons'>
      {coupons.map(c => (
        <button key={c.id} type='button' className='lm-pdp__coupon' onClick={() => claim(c)} disabled={c.id != null && claimed[c.id]}>
          <span className='lm-pdp__coupon-val'>−{couponValueShort(c)}</span>
          <span className='lm-pdp__coupon-min'>
            {c.min ? `over $${c.min}` : 'no minimum'}
            {isPercentCoupon(c) && c.discountCap ? ` · up to $${c.discountCap}` : ''}
          </span>
          <span className='lm-pdp__coupon-cta'>{c.id != null && claimed[c.id] ? 'Claimed' : 'Claim'}</span>
        </button>
      ))}
      <Link to='/coupons' className='lm-pdp__coupon-all small text-decoration-none align-self-center flex-shrink-0'>
        See all coupons <i className='bi bi-chevron-right' style={{ fontSize: '0.7em' }} />
      </Link>
    </div>
  );
};

export default CouponStrip;
