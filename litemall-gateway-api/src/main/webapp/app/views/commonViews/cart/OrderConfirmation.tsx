import React from 'react';
import { Link, useParams } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';
import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { Page, ResultPanel } from 'app/components/commonComponents/storefront';

/**
 * Post-placement confirmation. Reads the last placed order from the order slice
 * (set by placeOrder/payOrder.fulfilled); falls back to the :id route param so a
 * direct visit / refresh still shows the order reference.
 */
const OrderConfirmation: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const lastOrder = useAppSelector(state => state.order.data.lastOrder);

  const orderId = lastOrder?.orderId ?? (id ? Number(id) : undefined);

  const sub = (
    <>
      {orderId != null && (
        <p className='mb-1'>
          Your order reference is <strong>#{orderId}</strong>
          {lastOrder?.orderSn ? ` (${lastOrder.orderSn})` : ''}.
        </p>
      )}
      {lastOrder?.actualPrice != null && (
        <p className='mb-1'>
          Total charged: <span className='lm-amount'>${priceNum(lastOrder.actualPrice).toFixed(2)}</span>
        </p>
      )}
      {lastOrder?.paymentMethod && <p className='mb-0'>Paid via {lastOrder.paymentMethod === 'WALLET' ? 'digital wallet' : 'card'}.</p>}
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
