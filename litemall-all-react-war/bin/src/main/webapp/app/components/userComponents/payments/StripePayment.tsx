import { CardElement, useElements, useStripe } from '@stripe/react-stripe-js';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import React, { useState } from 'react';

const StripePaymentComponent: React.FC = () => {
  const stripe = useStripe();
  const elements = useElements();
  const [clientSecret, setClientSecret] = useState('');
  const createPaymentIntent = async () => {
    const paymentUrl = BASE_URL_CONTEXT + '/stripe/create-payment-intent';
    const response = await baseAxios.post(paymentUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ amount: 5000 }), // Amount in cents
    });
    const data = await response.data;
    console.log(data);
    setClientSecret(data.clientSecret);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!stripe || !elements) return;

    const cardElement = elements.getElement(CardElement);
    if (!cardElement) return;

    const { error, paymentIntent } = await stripe.confirmCardPayment(clientSecret, {
      payment_method: {
        card: cardElement,
      },
    });

    if (error) {
      console.error(error.message);
    } else {
      console.log('Payment successful!', paymentIntent);
    }
  };
  return (
    <form onSubmit={handleSubmit}>
      <CardElement />

      <button type='submit'>Pay</button>

      <button onClick={createPaymentIntent}>Initialize Payment</button>
    </form>
  );
};

export default StripePaymentComponent;
