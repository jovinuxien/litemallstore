import React, { useEffect } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';

import { Page, ResultPanel } from 'app/components/commonComponents/storefront';

/**
 * Payment result, modelled on litemall-vue `order/payment-status`. Reads the
 * outcome from the query param set by the Payment step (`status`/`result`), then
 * auto-redirects to the customer's orders after a short delay.
 */
const PaymentStatus: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const [params] = useSearchParams();
  const navigate = useNavigate();

  const outcome = params.get('status') ?? params.get('result');
  const ok = outcome !== 'fail' && outcome !== 'cancel' && outcome !== 'failed';

  // Auto-redirect to the orders list after ~3s.
  useEffect(() => {
    const t = setTimeout(() => navigate('/orders'), 3000);
    return () => clearTimeout(t);
  }, [navigate]);

  const sub = (
    <>
      {orderId && <p className='mb-1'>Order #{orderId}</p>}
      <p className='mb-0 text-muted'>Redirecting to your orders…</p>
    </>
  );

  const actions = (
    <Link to='/orders' className='btn btn-lm-primary'>
      View orders
    </Link>
  );

  return (
    <Page>
      <div className='container my-5'>
        <ResultPanel
          status={ok ? 'success' : 'fail'}
          title={ok ? 'Payment successful' : 'Payment failed'}
          sub={sub}
          actions={actions}
        />
      </div>
    </Page>
  );
};

export default PaymentStatus;
