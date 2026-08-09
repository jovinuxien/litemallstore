import React, { useState } from 'react';
import { Alert, Form } from 'react-bootstrap';
import { useNavigate, useParams } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { CheckoutPaymentMethod, payOrder } from 'app/shared/reducers/orderSlice';
import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { money } from 'app/shared/util/money';
import { Cell, CellGroup, Page, PageHead, PaymentBrandIcons, SubmitBar } from 'app/components/commonComponents/storefront';

/**
 * Standalone pay screen for an already-placed order, modelled on litemall-vue
 * `order/payment`. Picks a method (card / wallet) and charges the order through
 * the order slice's `payOrder` thunk (`POST /srv/order/{id}/actions/pay`).
 */
const Payment: React.FC = () => {
  const { orderId } = useParams<{ orderId: string }>();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();

  const { loading, errorMessage } = useAppSelector(state => state.order);
  // Match the route's orderId across the checkout's placed orders (local or CJ).
  const { local, cj } = useAppSelector(state => state.order.data.lastOrders);
  const lastOrder = [local, cj].find(o => o?.orderId === Number(orderId)) ?? null;

  const [paymentMethod, setPaymentMethod] = useState<CheckoutPaymentMethod>('CARD');

  const submitting = loading === 'pending';
  const orderSn = lastOrder?.orderSn ?? (orderId ? `#${orderId}` : '');
  const amountDue = lastOrder?.actualPrice != null ? priceNum(lastOrder.actualPrice) : undefined;

  const pay = async () => {
    if (!orderId) return;
    const result = await dispatch(payOrder({ orderId: Number(orderId), paymentMethod }));
    if (payOrder.fulfilled.match(result)) {
      navigate(`/pay/${orderId}/status?status=success`);
    }
  };

  return (
    <Page>
      <PageHead title='Payment' />
      <div className='container'>
        {/* Order summary */}
        <CellGroup title='Order'>
          <Cell title='Order no.' value={orderSn} />
          <Cell title='Amount due' value={<span className='lm-amount'>{money(amountDue)}</span>} />
        </CellGroup>

        {/* Payment method */}
        <CellGroup title='Payment method'>
          <Cell>
            <Form.Check
              type='radio'
              id='pm-card'
              name='pm'
              label={
                <>
                  Credit / debit card
                  <PaymentBrandIcons />
                </>
              }
              checked={paymentMethod === 'CARD'}
              onChange={() => setPaymentMethod('CARD')}
            />
          </Cell>
          <Cell>
            <Form.Check
              type='radio'
              id='pm-wallet'
              name='pm'
              label='Digital wallet'
              checked={paymentMethod === 'WALLET'}
              onChange={() => setPaymentMethod('WALLET')}
            />
          </Cell>
        </CellGroup>

        {errorMessage && <Alert variant='danger'>{errorMessage}</Alert>}
      </div>

      <SubmitBar total={amountDue} buttonText='Pay now' onSubmit={pay} loading={submitting} />
    </Page>
  );
};

export default Payment;
