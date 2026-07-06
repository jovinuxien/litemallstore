import React from 'react';
import { Link, useParams } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';
import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { Page, ResultPanel } from 'app/components/commonComponents/storefront';

/**
 * Post-placement confirmation. Reads the checkout's placed orders from the order
 * slice (up to one local + one CJ order, set by placeOrder/payOrder.fulfilled);
 * falls back to the :id route param so a direct visit / refresh still shows the
 * order reference. The CJ fulfilment number is not returned by pay — it lives on
 * the order's detail page in My Orders.
 */
const OrderConfirmation: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const { local, cj } = useAppSelector(state => state.order.data.lastOrders);

  const orders = [local, cj].filter((o): o is NonNullable<typeof o> => !!o);
  const routeId = id ? Number(id) : undefined;
  const totalCharged = orders.reduce((sum, o) => sum + (o.actualPrice != null ? priceNum(o.actualPrice) : 0), 0);
  const paymentMethod = orders.find(o => o.paymentMethod)?.paymentMethod;

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
      {totalCharged > 0 && (
        <p className='mb-1'>
          Total charged: <span className='lm-amount'>${totalCharged.toFixed(2)}</span>
        </p>
      )}
      {paymentMethod && <p className='mb-0'>Paid via {paymentMethod === 'WALLET' ? 'digital wallet' : 'card'}.</p>}
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
