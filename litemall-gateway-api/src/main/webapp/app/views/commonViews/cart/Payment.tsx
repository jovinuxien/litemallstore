import { Elements } from '@stripe/react-stripe-js';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Form, Spinner } from 'react-bootstrap';
import { Link, useNavigate, useParams } from 'react-router-dom';

import { useAppDispatch } from 'app/config/store';
import { useTranslation } from 'app/i18n';
import { orderApi } from 'app/shared/api';
import { loadSiteConfig } from 'app/shared/config/siteConfig';
import { IOrderDetail } from 'app/shared/model/order/order.model';
import { classifyIntentRefusal, paymentErrorText, settlementOf } from 'app/shared/payment/paymentOutcome';
import StripeCardForm, { StripeCardHandle } from 'app/shared/payment/StripeCardForm';
import { stripePromiseFor } from 'app/shared/payment/stripeLoader';
import { CheckoutPaymentMethod, payOrder } from 'app/shared/reducers/orderSlice';
import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { money } from 'app/shared/util/money';
import { Cell, CellGroup, Page, PageHead, PaymentBrandIcons, SubmitBar } from 'app/components/commonComponents/storefront';

/** What the card form is mounted with: the server-minted intent, never a client guess. */
interface MintedIntent {
  clientSecret: string;
  /** Minor units (integer cents), from the order's own total. */
  amount: number;
  /** ISO 4217, lower-cased the way Stripe writes it. */
  currency: string;
}

/**
 * Standalone pay screen for an order that is already placed and still unpaid — where
 * "Pay now" on the order list, the order page and the failed-payment page all lead.
 *
 * Card payment used to be a dead end here: the page offered "Credit / debit card" but
 * mounted no Stripe form, so there was never a PaymentIntent to report and every
 * attempt ended in "card payment was not completed". It now runs the same sequence as
 * the checkout — validate the card → confirm the intent with Stripe → report the real
 * intent id to `POST /srv/order/{id}/actions/pay` — through the same {@link StripeCardForm}.
 *
 * One difference from the checkout, and it is deliberate. The checkout must mount the
 * card form before an order exists, so it declares an amount and currency up front. Here
 * the order DOES exist, so the intent is minted first and the form is mounted with the
 * intent's own amount and currency: what the customer is shown, what Stripe offers
 * (payment methods depend on the currency) and what the server charges cannot differ.
 *
 * The page never decides an outcome itself. An order that is no longer payable, a bank
 * debit in flight, a redirect method and a finished payment all hand over to
 * `/pay/:orderId/status`, which reads the order until the server says what happened.
 */
const Payment: React.FC = () => {
  const { t } = useTranslation('checkout');
  const { orderId } = useParams<{ orderId: string }>();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();

  const [detail, setDetail] = useState<IOrderDetail | null>(null);
  const [load, setLoad] = useState<'loading' | 'ready' | 'failed'>('loading');
  const [publishableKey, setPublishableKey] = useState<string | null>(null);
  const [configLoaded, setConfigLoaded] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<CheckoutPaymentMethod>('CARD');
  const [intent, setIntent] = useState<MintedIntent | null>(null);
  // Why the card form could not be prepared — the server's words when it gave any.
  const [cardError, setCardError] = useState<string | null>(null);
  const [payError, setPayError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const cardRef = useRef<StripeCardHandle>(null);
  // One mint per attempt: an effect re-run (StrictMode, a re-render) must not mint twice.
  const mintingRef = useRef(false);
  // The intent Stripe has ALREADY charged in this visit. If reporting it to the order
  // service then fails (a timeout, a dropped connection), "Pay now" must re-send that
  // same id — never ask Stripe to confirm a second time for money already taken.
  const chargedIntentRef = useRef<string | null>(null);

  const statusUrl = (status?: 'success' | 'processing') => `/pay/${orderId}/status${status ? `?status=${status}` : ''}`;

  useEffect(() => {
    loadSiteConfig().then(cfg => {
      setPublishableKey(cfg.stripePublishableKey);
      setConfigLoaded(true);
    });
  }, []);

  // The order itself: the amount comes from the server, and an order that can no longer
  // be paid (already settled, or cancelled) is not offered a payment form at all.
  useEffect(() => {
    if (!orderId) return undefined;
    let cancelled = false;
    orderApi
      .detail(orderId)
      .then(d => {
        if (cancelled) return;
        const settlement = settlementOf(d);
        if (settlement === 'paid' || settlement === 'cancelled') {
          navigate(statusUrl(), { replace: true });
          return;
        }
        setDetail(d ?? null);
        setLoad('ready');
      })
      .catch(() => {
        if (!cancelled) setLoad('failed');
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderId]);

  /** Card payment needs a publishable key. Absent ⇒ unavailable, never stubbed. */
  const cardAvailable = !!publishableKey;

  // A deployment without Stripe must not strand the customer on a dead radio.
  useEffect(() => {
    if (configLoaded && !cardAvailable && paymentMethod === 'CARD') setPaymentMethod('WALLET');
  }, [configLoaded, cardAvailable, paymentMethod]);

  // Mint the intent once the customer is actually looking at the card option. The server
  // derives the amount from the order and refuses a second intent while one is settling.
  useEffect(() => {
    if (load !== 'ready' || !orderId || !cardAvailable || paymentMethod !== 'CARD') return;
    if (intent || cardError || mintingRef.current) return;
    mintingRef.current = true;
    orderApi
      .paymentIntent(orderId)
      .then(minted => {
        setIntent({ clientSecret: minted.clientSecret, amount: minted.amount, currency: String(minted.currency ?? '').toLowerCase() });
      })
      .catch(error => {
        const message = paymentErrorText(error);
        const refusal = classifyIntentRefusal(message);
        if (refusal === 'already-paid') {
          // The webhook (or another tab) settled the order first — success, not an error.
          navigate(statusUrl(), { replace: true });
          return;
        }
        if (refusal === 'processing') {
          // A bank debit for this order is still in flight: no second payment is offered.
          navigate(statusUrl('processing'), { replace: true });
          return;
        }
        // Stripe disabled or failing ⇒ said plainly. The order stays unpaid; nothing is invented.
        setCardError(message || t('errors.cardUnavailable'));
      })
      .finally(() => {
        mintingRef.current = false;
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [load, orderId, cardAvailable, paymentMethod, intent, cardError]);

  const elementsOptions = useMemo(
    () => (intent && intent.amount > 0 && intent.currency ? { mode: 'payment' as const, amount: intent.amount, currency: intent.currency } : null),
    [intent],
  );

  const orderSn = detail?.orderSn ?? (orderId ? `#${orderId}` : '');
  const amountDue = detail?.actualPrice != null ? priceNum(detail.actualPrice) : undefined;
  const cardReady = paymentMethod !== 'CARD' || (cardAvailable && !!elementsOptions);

  const pay = async () => {
    if (!orderId || busy || !cardReady) return;
    setPayError(null);
    setBusy(true);
    try {
      let paymentIntentId: string | undefined;
      if (paymentMethod === 'CARD') {
        if (!chargedIntentRef.current) {
          if (!intent || !cardRef.current) return;
          if (!(await cardRef.current.validate())) return; // the field errors render inline
          const outcome = await cardRef.current.confirm(intent.clientSecret, orderId);
          if (outcome.kind === 'processing') {
            // SEPA/bank debit: in flight for days and WILL settle. Not a failure, not a retry.
            navigate(statusUrl('processing'));
            return;
          }
          // failed ⇒ the card form has rendered the reason; the same intent can be retried.
          if (outcome.kind !== 'succeeded') return;
          chargedIntentRef.current = outcome.paymentIntentId;
        }
        paymentIntentId = chargedIntentRef.current;
      }
      const result = await dispatch(payOrder({ orderId: Number(orderId), paymentMethod, paymentIntentId }));
      if (payOrder.fulfilled.match(result)) {
        navigate(statusUrl('success'));
        return;
      }
      const message = result.payload?.errmsg ?? '';
      if (classifyIntentRefusal(message) === 'already-paid') {
        navigate(statusUrl());
        return;
      }
      // The server's own words — including "refunded automatically" for a late payment.
      setPayError(message || t('status.failed'));
    } finally {
      setBusy(false);
    }
  };

  if (load === 'loading') {
    return (
      <Page>
        <PageHead title={t('pay.title')} />
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      </Page>
    );
  }

  if (load === 'failed') {
    return (
      <Page>
        <PageHead title={t('pay.title')} />
        <div className='container'>
          <Alert variant='warning'>{t('pay.loadFailed')}</Alert>
          <Link to='/orders' className='btn btn-lm-outline'>
            {t('status.viewOrders')}
          </Link>
        </div>
      </Page>
    );
  }

  return (
    <Page>
      <PageHead title={t('pay.title')} />
      <div className='container'>
        {/* Order summary */}
        <CellGroup title={t('pay.order')}>
          <Cell title={t('pay.orderNo')} value={orderSn} />
          <Cell title={t('pay.amountDue')} value={<span className='lm-amount'>{money(amountDue)}</span>} />
        </CellGroup>

        {/* Payment method */}
        <CellGroup title={t('pay.method')}>
          <Cell>
            <Form.Check
              type='radio'
              id='pm-card'
              name='pm'
              label={
                <>
                  {t('payment.card')}
                  <PaymentBrandIcons muted={!cardAvailable} />
                </>
              }
              checked={paymentMethod === 'CARD'}
              disabled={!cardAvailable || busy}
              onChange={() => setPaymentMethod('CARD')}
            />
            {configLoaded && !cardAvailable && <div className='form-text'>{t('payment.cardUnavailable')}</div>}
          </Cell>
          {paymentMethod === 'CARD' && cardAvailable && (
            <div className='p-3' data-card-state={elementsOptions ? 'ready' : cardError ? 'unavailable' : 'preparing'}>
              {elementsOptions ? (
                <Elements stripe={stripePromiseFor(publishableKey!)} options={elementsOptions}>
                  <StripeCardForm ref={cardRef} disabled={busy} />
                </Elements>
              ) : cardError ? (
                <Alert variant='warning' className='mb-0'>
                  {cardError}{' '}
                  <button type='button' className='btn btn-link p-0 align-baseline' onClick={() => setCardError(null)}>
                    {t('errors.retry')}
                  </button>
                </Alert>
              ) : (
                <span className='text-muted small'>
                  <Spinner as='span' animation='border' size='sm' className='me-2' />
                  {t('payment.preparingCard')}
                </span>
              )}
            </div>
          )}
          <Cell>
            <Form.Check
              type='radio'
              id='pm-wallet'
              name='pm'
              label={t('pay.wallet')}
              checked={paymentMethod === 'WALLET'}
              disabled={busy}
              onChange={() => setPaymentMethod('WALLET')}
            />
          </Cell>
        </CellGroup>

        {payError && <Alert variant='danger'>{payError}</Alert>}
      </div>

      <SubmitBar total={amountDue} buttonText={t('pay.payNow')} onSubmit={pay} loading={busy} disabled={!cardReady} />
    </Page>
  );
};

export default Payment;
