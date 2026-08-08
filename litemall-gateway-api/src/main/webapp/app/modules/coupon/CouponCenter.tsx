import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';

import { ICoupon, userApi } from 'app/shared/api';
import { ApiError } from 'app/shared/api/http';
import { EmptyState, Page, PageHead } from 'app/components/commonComponents/storefront';
import { useAppSelector } from 'app/config/store';
import { couponConditionLabel, couponScopeLink, couponValueShort, isPercentCoupon } from 'app/shared/util/couponFormat';
import { resetPageTitle, setPageTitle } from 'app/shared/util/pageTitle';

/**
 * Wave-18 public coupon center (`/coupons`): every claimable coupon from
 * `GET /srv/coupon/list` (public read), claim via `POST /srv/coupon/receive`
 * (authenticated — logged-out visitors are diverted to /login and come back).
 * Scoped coupons deep-link "shop eligible items" to their category landing
 * (`/category/<id>`, Wave-9) or /search; percent coupons render
 * "N% off (up to $C)" per the Wave-18 contract. Entry points: header account
 * menu, footer, and the PDP CouponStrip's "see all".
 */

/** Per-coupon claim UI state; string = honest backend error to show inline. */
type ClaimState = 'busy' | 'claimed' | { error: string };

const CouponCenter: React.FC = () => {
  const navigate = useNavigate();
  const isAuthenticated = useAppSelector(state => state.customerAuth.data.isAuthenticated);

  const [coupons, setCoupons] = useState<ICoupon[]>([]);
  const [loading, setLoading] = useState(true);
  const [claims, setClaims] = useState<Record<number, ClaimState>>({});

  useEffect(() => {
    setPageTitle('Coupons');
    let cancelled = false;
    userApi
      .couponList({ page: 1, limit: 60 })
      .then(res => {
        if (!cancelled) setCoupons(res?.list ?? []);
      })
      .catch(() => {
        // Promotion down/unreachable ⇒ empty state, never a broken page.
        if (!cancelled) setCoupons([]);
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
      resetPageTitle();
    };
  }, []);

  const claim = async (c: ICoupon) => {
    if (c.id == null) return;
    if (!isAuthenticated) {
      // Same divert-and-return shape as CollectButton: sign in, land back here.
      navigate('/login', { state: { from: { pathname: '/coupons' } } });
      return;
    }
    const id = c.id;
    const state = claims[id];
    if (state === 'busy' || state === 'claimed') return;
    setClaims(prev => ({ ...prev, [id]: 'busy' }));
    try {
      await userApi.couponReceive(id);
      setClaims(prev => ({ ...prev, [id]: 'claimed' }));
    } catch (e) {
      // Honest errno message (already claimed / limit reached / expired).
      const msg = e instanceof ApiError && e.message ? e.message : 'Could not claim this coupon.';
      setClaims(prev => ({ ...prev, [id]: { error: msg } }));
    }
  };

  return (
    <Page>
      <PageHead
        title='Coupons'
        sub={
          isAuthenticated ? (
            <>
              Claim a coupon below, then pick it at checkout. <Link to='/user/coupons'>View my coupons</Link>
              {' · '}
              {/* Wave-19: search preset — the coupon_flag=1 deep-link filters to
                  products an active claimable coupon covers (searchRouting maps
                  it onto the "Has coupon" toggle). */}
              <Link to='/search?coupon_flag=1'>Find couponed products</Link>
            </>
          ) : (
            <>
              Claim a coupon and it is applied from your account at checkout.{' '}
              <Link to='/login' state={{ from: { pathname: '/coupons' } }}>
                Sign in
              </Link>{' '}
              to claim. <Link to='/search?coupon_flag=1'>Find couponed products</Link>
            </>
          )
        }
      />
      <div className='container'>
        {loading ? (
          <div className='text-center my-5'>
            <Spinner animation='border' />
          </div>
        ) : coupons.length === 0 ? (
          <EmptyState icon='bi-ticket-perforated' text='No coupons on offer right now — check back soon.'>
            <Link to='/search' className='btn btn-sm btn-outline-primary mt-2'>
              Shop the store
            </Link>
          </EmptyState>
        ) : (
          <div className='d-grid gap-3 my-3'>
            {coupons.map(c => {
              const state = c.id != null ? claims[c.id] : undefined;
              const claimedNow = state === 'claimed';
              const scope = couponScopeLink(c);
              return (
                <div key={c.id} className='lm-coupon-card'>
                  <div className='lm-coupon-card__value'>
                    <div className='lm-coupon-card__amt'>{couponValueShort(c)}</div>
                    <div className='lm-coupon-card__cond'>{isPercentCoupon(c) ? 'off your order' : 'off'}</div>
                  </div>
                  <div className='lm-coupon-card__body'>
                    <div className='lm-coupon-card__name'>{c.name}</div>
                    <div className='lm-coupon-card__desc'>
                      {couponConditionLabel(c)}
                      {c.desc || c.tag ? ` · ${c.desc || c.tag}` : ''}
                    </div>
                    {(c.startTime || c.endTime) && (
                      <div className='lm-coupon-card__dates'>{[c.startTime, c.endTime].filter(Boolean).join(' – ')}</div>
                    )}
                    <div className='d-flex align-items-center gap-2 flex-wrap mt-2'>
                      <button
                        type='button'
                        className={`btn btn-sm ${claimedNow ? 'btn-outline-success' : 'btn-lm-primary'}`}
                        disabled={state === 'busy' || claimedNow}
                        onClick={() => claim(c)}
                      >
                        {state === 'busy' ? 'Claiming…' : claimedNow ? 'Claimed ✓' : isAuthenticated ? 'Claim' : 'Sign in to claim'}
                      </button>
                      <Link to={scope.to} className='small text-decoration-none'>
                        {scope.label} <i className='bi bi-chevron-right' style={{ fontSize: '0.7em' }} />
                      </Link>
                      {claimedNow && (
                        <Link to='/user/coupons' className='small text-decoration-none'>
                          View my coupons
                        </Link>
                      )}
                    </div>
                    {typeof state === 'object' && (
                      <div className='small text-danger mt-1' role='status'>
                        {state.error}
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </Page>
  );
};

export default CouponCenter;
