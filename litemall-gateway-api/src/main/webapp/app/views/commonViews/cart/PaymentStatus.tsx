import React from 'react';
import { Button, Card, Container } from 'react-bootstrap';
import { Link, useParams, useSearchParams } from 'react-router-dom';

/**
 * Payment result, modelled on litemall-vue `order/payment-status`. Reads the
 * `result` query param (success|fail) set by the Payment step.
 */
const PaymentStatus: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const [params] = useSearchParams();
  const ok = params.get('result') !== 'fail';

  return (
    <Container className='my-5' style={{ maxWidth: 520 }}>
      <Card className='text-center shadow-sm'>
        <Card.Body className='py-5'>
          <div className={ok ? 'text-success' : 'text-danger'} style={{ fontSize: '3rem' }}>
            <i className={`bi ${ok ? 'bi-check-circle-fill' : 'bi-x-circle-fill'}`} />
          </div>
          <h2>{ok ? 'Payment successful' : 'Payment failed'}</h2>
          {orderId && <p className='lead'>Order #{orderId}</p>}
          {!ok && <p className='text-muted'>No charge was made. You can retry payment from your orders.</p>}
          <div className='mt-4 d-flex gap-2 justify-content-center'>
            {ok ? (
              <Link to={`/order/${orderId}`} className='btn btn-outline-primary'>
                View order
              </Link>
            ) : (
              <Link to={`/pay/${orderId}`} className='btn btn-primary'>
                Retry payment
              </Link>
            )}
            <Button as={Link as any} to='/orders' variant='outline-secondary'>
              My orders
            </Button>
          </div>
        </Card.Body>
      </Card>
    </Container>
  );
};

export default PaymentStatus;
