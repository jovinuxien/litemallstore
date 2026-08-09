import React, { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';
import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { money } from 'app/shared/util/money';
import { authApi, orderApi } from 'app/shared/api';
import { IOrderDetail } from 'app/shared/model/order/order.model';
import { trackPurchase } from 'app/shared/tracking/ecommerce';
import { orderAddressLines } from 'app/shared/util/address';

/**
 * Wave 16: guest claim — shown only when the current session is a guest
 * shadow account. Setting a password turns it into a real account without
 * losing the session or the just-placed order.
 */
const GuestClaimCard: React.FC = () => {
  const [state, setState] = useState<'hidden' | 'offer' | 'busy' | 'done'>('hidden');
  const [email, setEmail] = useState<string | null>(null);
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    authApi
      .me()
      .then(env => {
        if (env.errno === 0 && env.data?.isGuest) {
          setEmail(env.data.email ?? null);
          setState('offer');
        }
      })
      .catch(() => undefined);
  }, []);

  if (state === 'hidden') return null;
  if (state === 'done') {
    return (
      <div className='alert alert-success text-start mx-auto my-3' style={{ maxWidth: 420 }}>
        <i className='bi bi-check-circle me-2' />
        Account created — next time, sign in with <strong>{email}</strong> to see your orders.
      </div>
    );
  }
  const submit = async () => {
    if (password.length < 8 || state === 'busy') return;
    setState('busy');
    setError(null);
    try {
      const env = await authApi.guestClaim({ password });
      if (env.errno === 0) {
        setState('done');
      } else {
        setError(env.errmsg ?? 'Could not create the account.');
        setState('offer');
      }
    } catch {
      setError('Could not create the account — please try again.');
      setState('offer');
    }
  };
  return (
    <div className='border rounded p-3 text-start mx-auto my-3' style={{ maxWidth: 420 }}>
      <div className='fw-semibold mb-1'>Keep your order history</div>
      <p className='small text-muted mb-2'>
        Create a password for <strong>{email ?? 'your email'}</strong> to track this order and check out faster next
        time.
      </p>
      <input
        type='password'
        className='form-control mb-2'
        placeholder='Choose a password (min 8 characters)'
        value={password}
        onChange={e => setPassword(e.target.value)}
        onKeyDown={e => {
          if (e.key === 'Enter') submit();
        }}
      />
      {error && (
        <div className='small text-danger mb-2' role='status'>
          {error}
        </div>
      )}
      <button type='button' className='btn btn-lm-primary w-100' disabled={password.length < 8 || state === 'busy'} onClick={submit}>
        {state === 'busy' ? 'Creating…' : 'Create account'}
      </button>
    </div>
  );
};
import { OrderSummary, Page, ResultPanel } from 'app/components/commonComponents/storefront';

/**
 * Post-placement confirmation. Reads the checkout's placed orders from the order
 * slice (up to one local + one CJ order, set by placeOrder/payOrder.fulfilled);
 * falls back to the :id route param so a direct visit / refresh still shows the
 * order reference. Each order's money breakdown (goods / shipping / coupon /
 * total) is fetched from the live /srv/order/detail — best-effort: a fetch
 * failure just leaves the plain reference lines. The CJ fulfilment number is
 * not returned by pay — it lives on the order's detail page in My Orders.
 */
const OrderConfirmation: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const { local, cj } = useAppSelector(state => state.order.data.lastOrders);

  const orders = [local, cj].filter((o): o is NonNullable<typeof o> => !!o);
  const routeId = id ? Number(id) : undefined;
  const totalCharged = orders.reduce((sum, o) => sum + (o.actualPrice != null ? priceNum(o.actualPrice) : 0), 0);
  const paymentMethod = orders.find(o => o.paymentMethod)?.paymentMethod;

  // Price breakdown per order id, from the (owner-scoped) order detail.
  const [details, setDetails] = useState<Record<number, IOrderDetail>>({});
  const orderIds = orders.length > 0 ? orders.map(o => o.orderId) : routeId != null && routeId > 0 ? [routeId] : [];
  const idsKey = orderIds.join(',');
  useEffect(() => {
    let cancelled = false;
    orderIds.forEach(orderId => {
      orderApi
        .detail(orderId)
        .then(d => {
          if (!cancelled && d?.id != null) {
            setDetails(prev => ({ ...prev, [orderId]: d }));
            // Funnel purchase (Wave 15): server-computed money; the facade
            // session-guards per order id so reloads never double-count.
            trackPurchase({
              orderId,
              revenue: priceNum(d.actualPrice ?? 0),
              subtotal: priceNum(d.goodsPrice ?? 0),
              shipping: priceNum(d.freightPrice ?? 0),
              discount: priceNum(d.couponPrice ?? 0),
            });
          }
        })
        .catch(() => undefined);
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idsKey]);

  const breakdownRows = (d: IOrderDetail) => {
    const couponPrice = priceNum(d.couponPrice ?? 0);
    const freightPrice = priceNum(d.freightPrice ?? 0);
    return [
      { label: 'Goods total', value: money(priceNum(d.goodsPrice ?? 0)) },
      { label: 'Shipping', value: freightPrice > 0 ? money(freightPrice) : 'Free', variant: 'muted' as const },
      ...(couponPrice > 0 ? [{ label: 'Coupon', value: `−${money(couponPrice)}`, variant: 'success' as const }] : []),
      { label: 'Total charged', value: money(priceNum(d.actualPrice ?? 0)), variant: 'total' as const },
    ];
  };
  const fetchedDetails = orderIds.map(orderId => details[orderId]).filter((d): d is IOrderDetail => !!d);

  const sub = (
    <>
      {orders.map(o => (
        <p className='mb-1' key={o.orderId}>
          Your {o.group === 'cj' ? 'dropship ' : ''}order reference is <strong>#{o.orderId}</strong>
          {o.orderSn ? ` (${o.orderSn})` : ''}.
        </p>
      ))}
      {orders.length === 0 && routeId != null && routeId > 0 && (
        <p className='mb-1'>
          Your order reference is <strong>#{routeId}</strong>.
        </p>
      )}
      {cj && (
        <p className='mb-1 text-muted small'>The CJ fulfilment number appears on the order’s detail page in My Orders.</p>
      )}
      {fetchedDetails.length > 0 ? (
        <div className='text-start mx-auto my-3' style={{ maxWidth: 360 }}>
          {fetchedDetails.map(d => (
            <div key={d.id} className='mb-2'>
              {fetchedDetails.length > 1 && <div className='small text-muted mb-1'>Order #{d.id}</div>}
              <OrderSummary rows={breakdownRows(d)} />
            </div>
          ))}
          {/* Delivery address (once — both orders of a split cart ship to the
              same snapshot). Pickup orders show their store on the detail page. */}
          {(() => {
            const withAddress = fetchedDetails.find(d => d.consignee && d.address && !d.address.startsWith('PICKUP: '));
            if (!withAddress) return null;
            return (
              <div className='border rounded p-3 mt-2 small'>
                <div className='text-muted mb-1'>
                  <i className='bi bi-geo-alt me-1' />
                  Delivering to
                </div>
                <div className='fw-semibold'>
                  {withAddress.consignee}
                  {withAddress.mobile ? <span className='text-muted fw-normal'> · {withAddress.mobile}</span> : null}
                </div>
                {orderAddressLines(withAddress.address).map(line => (
                  <div key={line}>{line}</div>
                ))}
              </div>
            );
          })()}
        </div>
      ) : (
        totalCharged > 0 && (
          <p className='mb-1'>
            Total charged: <span className='lm-amount'>${totalCharged.toFixed(2)}</span>
          </p>
        )
      )}
      {paymentMethod && <p className='mb-0'>Paid via {paymentMethod === 'WALLET' ? 'digital wallet' : 'card'}.</p>}
      <GuestClaimCard />
    </>
  );

  const actions = (
    <>
      <Link to='/orders' className='btn btn-lm-outline'>
        View my orders
      </Link>
      <Link to='/' className='btn btn-lm-primary'>
        Continue shopping
      </Link>
    </>
  );

  return (
    <Page>
      <div className='container my-5'>
        <ResultPanel status='success' title='Thank you for your order!' sub={sub} actions={actions} />
      </div>
    </Page>
  );
};

export default OrderConfirmation;
