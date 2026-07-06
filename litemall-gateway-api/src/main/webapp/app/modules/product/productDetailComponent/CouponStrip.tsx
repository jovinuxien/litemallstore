import React, { useEffect, useState } from 'react';

import { ICoupon, userApi, isMissingEndpoint } from 'app/shared/api';

/**
 * Receivable-coupon strip on the detail page, mirroring litemall-vue's coupon
 * row. Reads public coupons (`/srv/coupon/list`) and lets the customer claim
 * one (`/srv/coupon/receive`). Renders nothing until/unless the endpoint is
 * live and returns coupons — so the page is unaffected by the follow-up gap.
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
      .catch(e => {
        if (!cancelled && !isMissingEndpoint(e)) setCoupons([]);
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
          <span className='lm-pdp__coupon-val'>−${c.discount ?? 0}</span>
          <span className='lm-pdp__coupon-min'>{c.min ? `over $${c.min}` : 'no minimum'}</span>
          <span className='lm-pdp__coupon-cta'>{c.id != null && claimed[c.id] ? 'Claimed' : 'Claim'}</span>
        </button>
      ))}
    </div>
  );
};

export default CouponStrip;
