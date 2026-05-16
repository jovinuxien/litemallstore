import StripePayment from 'app/components/userComponents/payments/StripePayment';
import React from 'react';

const PaymentView: React.FC = () => {
  const [method, setMethod] = React.useState('');
  return (
    <div>
      = <h1>Choose Payment Method</h1>
      <button onClick={() => setMethod('stripe')}>Pay with Stripe</button>
      <button onClick={() => setMethod('paypal')}>Pay with PayPal</button>
      {method === 'stripe' && <StripePayment />}
      {/*       {method === 'paypal' && <PayPalPayment />}
       */}{' '}
    </div>
  );
};

export default PaymentView;
