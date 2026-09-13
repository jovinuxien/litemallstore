import React, { useEffect, useRef, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';

import { Page, ResultPanel } from 'app/components/commonComponents/storefront';
import { orderApi } from 'app/shared/api';
import { useTranslation } from 'app/i18n';
import { redirectOutcome, settlementOf } from 'app/shared/payment/paymentOutcome';

/** Poll cadence and budget: 3 s × 40 = two minutes before the page stops asking. */
export const POLL_MS = 3000;
export const MAX_POLLS = 40;
/** Pause on the success panel before moving to the order page. */
const REDIRECT_MS = 3000;

type Phase = 'confirming' | 'processing' | 'paid' | 'cancelled' | 'failed' | 'timeout';

/**
 * Payment result page — the `return_url` for every Stripe redirect method (Klarna, iDEAL,
 * Bancontact, 3-DS), the hand-off target for a `processing` bank debit, and where the
 * wallet pay page lands too.
 *
 * It does not decide anything itself. The order service marks the order paid from
 * Stripe's webhook after re-verifying the intent, so this page WAITS: it polls the
 * customer detail until the order is no longer payable (paid or cancelled) and only then
 * says so. A `redirect_status=succeeded` in the URL is not taken on trust; only `failed`
 * is terminal without asking (order lifecycle contract §1).
 */
const PaymentStatus: React.FC = () => {
  const { t } = useTranslation('checkout');
  const { orderId } = useParams<{ orderId: string }>();
  const [params] = useSearchParams();
  const navigate = useNavigate();

  const initial = redirectOutcome(params);
  const [phase, setPhase] = useState<Phase>(initial === 'failed' ? 'failed' : initial === 'processing' ? 'processing' : 'confirming');
  const [statusText, setStatusText] = useState<string | undefined>();
  // Whether the URL said "processing": on timeout that copy stays (the bank really is
  // slow), while a plain confirmation that never lands turns into "still waiting".
  const bankProcessing = useRef(initial === 'processing');

  useEffect(() => {
    if (initial === 'failed' || !orderId) return undefined;
    let stopped = false;
    let attempts = 0;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const tick = async () => {
      attempts += 1;
      try {
        const detail = await orderApi.detail(orderId);
        if (stopped) return;
        if (detail?.orderStatusText) setStatusText(detail.orderStatusText);
        const settlement = settlementOf(detail);
        if (settlement === 'paid') {
          setPhase('paid');
          return;
        }
        if (settlement === 'cancelled') {
          setPhase('cancelled');
          return;
        }
      } catch {
        // Transient (or a session that has not re-hydrated after the redirect yet):
        // keep waiting — the page must never claim an outcome it did not read.
      }
      if (stopped) return;
      if (attempts >= MAX_POLLS) {
        if (!bankProcessing.current) setPhase('timeout');
        return;
      }
      timer = setTimeout(tick, POLL_MS);
    };

    tick();
    return () => {
      stopped = true;
      if (timer) clearTimeout(timer);
    };
  }, [orderId, initial]);

  // Settled: pause on the confirmation, then open the order itself.
  useEffect(() => {
    if (phase !== 'paid' || !orderId) return undefined;
    const timer = setTimeout(() => navigate(`/order/${orderId}`), REDIRECT_MS);
    return () => clearTimeout(timer);
  }, [phase, orderId, navigate]);

  const orderLine = orderId ? <p className='mb-1'>{t('status.order', { id: orderId })}</p> : null;
  const viewOrder = orderId ? (
    <Link to={`/order/${orderId}`} className='btn btn-lm-primary'>
      {t('status.viewOrder')}
    </Link>
  ) : null;
  const viewOrders = (
    <Link to='/orders' className='btn btn-lm-outline'>
      {t('status.viewOrders')}
    </Link>
  );

  let status: 'success' | 'fail' | 'pending' = 'pending';
  let title: string;
  let body: React.ReactNode;
  let actions: React.ReactNode;

  switch (phase) {
    case 'paid':
      status = 'success';
      title = t('status.success');
      body = (
        <>
          {orderLine}
          {statusText && <p className='mb-1'>{statusText}</p>}
          <p className='mb-0 text-muted'>{t('status.redirectingOrder')}</p>
        </>
      );
      actions = (
        <>
          {viewOrder}
          {viewOrders}
        </>
      );
      break;
    case 'failed':
      status = 'fail';
      title = t('status.failed');
      body = (
        <>
          {orderLine}
          <p className='mb-0 text-muted'>{t('status.failedBody')}</p>
        </>
      );
      actions = (
        <>
          {orderId && (
            <Link to={`/pay/${orderId}`} className='btn btn-lm-primary'>
              {t('status.tryAgain')}
            </Link>
          )}
          {viewOrders}
        </>
      );
      break;
    case 'cancelled':
      status = 'fail';
      title = t('status.cancelled');
      body = (
        <>
          {orderLine}
          <p className='mb-0 text-muted'>{t('status.cancelledBody')}</p>
        </>
      );
      actions = (
        <>
          {viewOrder}
          {viewOrders}
        </>
      );
      break;
    case 'processing':
      title = t('status.processing');
      body = (
        <>
          {orderLine}
          <p className='mb-0'>{t('status.processingBody')}</p>
        </>
      );
      actions = (
        <>
          {viewOrder}
          {viewOrders}
        </>
      );
      break;
    case 'timeout':
      title = t('status.stillWaiting');
      body = (
        <>
          {orderLine}
          <p className='mb-0'>{t('status.stillWaitingBody')}</p>
        </>
      );
      actions = (
        <>
          {viewOrder}
          {viewOrders}
        </>
      );
      break;
    default:
      title = t('status.confirming');
      body = (
        <>
          {orderLine}
          <p className='mb-0 text-muted'>
            <Spinner as='span' animation='border' size='sm' className='me-2' />
            {t('status.confirmingBody')}
          </p>
        </>
      );
      actions = viewOrders;
  }

  return (
    <Page>
      <div className='container my-5' data-phase={phase}>
        <ResultPanel status={status} title={title} sub={body} actions={actions} />
      </div>
    </Page>
  );
};

export default PaymentStatus;
