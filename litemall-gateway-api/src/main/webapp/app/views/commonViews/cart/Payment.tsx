import React, { useState } from 'react';
import { Alert, Button, Card, Container, Form, Spinner } from 'react-bootstrap';
import { useNavigate, useParams } from 'react-router-dom';

import { isMissingEndpoint, orderApi } from 'app/shared/api';

/**
 * Payment step for an already-placed order, modelled on litemall-vue
 * `order/payment`. Picks a method (card / wallet) and triggers the order
 * service's prepay (`/srv/order/prepay`). Until prepay is live, a missing
 * endpoint is treated as an optimistic success so the flow can be walked
 * end-to-end (follow-up: order worktree).
 */
const Payment: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const navigate = useNavigate();
  const [method, setMethod] = useState<'CARD' | 'WALLET'>('CARD');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const pay = async () => {
    if (!orderId) return;
    setBusy(true);
    setError(null);
    try {
      await orderApi.prepay(orderId);
      navigate(`/pay/${orderId}/status?result=success`);
    } catch (e) {
      if (isMissingEndpoint(e)) {
        navigate(`/pay/${orderId}/status?result=success`);
        return;
      }
      setError((e as { message?: string })?.message ?? 'Payment failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Container className='my-5' style={{ maxWidth: 480 }}>
      <Card className='shadow-sm'>
        <Card.Body>
          <h3 className='mb-1'>Pay for your order</h3>
          <p className='text-muted'>Order #{orderId}</p>

          <Form.Check
            type='radio'
            id='pm-card'
            name='pm'
            label='Credit / debit card'
            checked={method === 'CARD'}
            onChange={() => setMethod('CARD')}
          />
          <Form.Check
            type='radio'
            id='pm-wallet'
            name='pm'
            label='Digital wallet (balance)'
            checked={method === 'WALLET'}
            onChange={() => setMethod('WALLET')}
          />

          {error && (
            <Alert variant='danger' className='mt-3'>
              {error}
            </Alert>
          )}

          <Button variant='success' className='w-100 mt-3' onClick={pay} disabled={busy}>
            {busy ? <Spinner animation='border' size='sm' /> : 'Pay now'}
          </Button>
        </Card.Body>
      </Card>
    </Container>
  );
};

export default Payment;
