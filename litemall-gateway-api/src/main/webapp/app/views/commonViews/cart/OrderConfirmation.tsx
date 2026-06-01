import React from 'react';
import { Button, Card, Container } from 'react-bootstrap';
import { Link, useParams } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';

/**
 * Post-placement confirmation. Reads the last placed order from the order slice
 * (set by placeOrder.fulfilled); falls back to the :id route param so a direct
 * visit / refresh still shows the order reference.
 */
const OrderConfirmation: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const lastOrder = useAppSelector(state => state.order.data.lastOrder);

  const orderId = lastOrder?.orderId ?? (id ? Number(id) : undefined);

  return (
    <Container className='my-5'>
      <Card className='text-center shadow-sm'>
        <Card.Body className='py-5'>
          <div className='text-success mb-3' style={{ fontSize: '3rem' }}>
            <i className='bi bi-check-circle-fill' />
          </div>
          <h2>Thank you for your order!</h2>
          {orderId != null && (
            <p className='lead'>
              Your order reference is <strong>#{orderId}</strong>
              {lastOrder?.orderSn ? ` (${lastOrder.orderSn})` : ''}.
            </p>
          )}
          {lastOrder?.actualPrice != null && <p className='text-muted'>Total charged: ${lastOrder.actualPrice}</p>}
          {lastOrder?.paymentMethod && <p className='text-muted'>Paid via {lastOrder.paymentMethod === 'WALLET' ? 'digital wallet' : 'card'}.</p>}
          <div className='mt-4 d-flex gap-2 justify-content-center'>
            <Link to='/orders' className='btn btn-outline-primary'>
              View my orders
            </Link>
            <Button as={Link as any} to='/' variant='primary'>
              Continue shopping
            </Button>
          </div>
        </Card.Body>
      </Card>
    </Container>
  );
};

export default OrderConfirmation;
