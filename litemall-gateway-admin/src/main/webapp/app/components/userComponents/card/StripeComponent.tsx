import { CardElement, Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js';
import { loadStripe } from '@stripe/stripe-js';
import { BASE_URL_CONTEXT } from 'app/config/api';
import renderFormField from 'app/helpers/renderFormField';
import axios from 'axios';
import React, { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';

const stripePromise = loadStripe('pk_test_7ZlwiqEnPTuI80hU5JPBHnX5');
const defaultSecret = 'sk_test_3Li1wcFBsz0AeFTSHBOGm5Yb';

interface StripePaymentComponentProps {
  amount: number;
  currency: string;
}

const StripePaymentForm: React.FC = () => {
  const stripe = useStripe();
  const elements = useElements();
  const { control, handleSubmit } = useForm();
  const [error, setError] = React.useState<Error | null>(null);
  const [loading, setLoading] = React.useState(false);

  const [paymentSate, setPaymentState] = React.useState({
    cardName: '',
    number: '',
    exp_month: '',
    exp_year: '',
    cvc: '',
  });

  const handleFullNameChange = (evt: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = evt.target;
    setPaymentState(prevState => ({
      ...prevState,
      [name]: value,
    }));
  };

  const handlePayment = async data => {
    if (!stripe || !elements) return;

    setLoading(true);
    setError(null);

    try {
      const result = await stripe.confirmPayment({
        elements,
        confirmParams: {
          return_url: `${window.location.origin}/payment-confirmation`,
        },
      });

      if ('paymentIntent' in result) {
        /* console.log('Payment succeeded:', result.paymentIntent); */
      } else if (result.error) {
        throw result.error;
      }
    } catch (error) {
      setError(error);
    } finally {
      setLoading(false);
    }
  };

  const onSubmit = async data => {
    if (!stripe || !elements) return;

    const cardElement = elements.getElement(CardElement);
    if (!cardElement) return;
  };

  return (
    <div className='row g-3'>
      <form onSubmit={handleSubmit(onSubmit)}>
        <div className='col-md-12'>
          {renderFormField({
            input: {
              name: 'Full Name',
              value: paymentSate.cardName,
              onChange: handleFullNameChange,
            },
            label: 'Card Name',
            required: true,
            tips: '',
            type: 'text',
            meta: { touched: true, error: undefined, warning: undefined },
            id: '',
          })}
        </div>
        <div className='col-md-12'>
          {renderFormField({
            input: {
              name: 'Amount',
              value: paymentSate.cardName,
              onChange: handleFullNameChange,
            },
            label: 'Amount',
            required: true,
            tips: '',
            type: 'text',
            meta: { touched: true, error: undefined, warning: undefined },
            id: '',
          })}
        </div>
        <div className='col-md-12'>
          {renderFormField({
            input: {
              name: 'cs',
              value: paymentSate.cardName,
              onChange: handleFullNameChange,
            },
            label: 'Card Number',
            required: true,
            tips: '',
            type: 'text',
            meta: { touched: true, error: undefined, warning: undefined },
            id: '',
          })}
        </div>
        <PaymentElement />

        {error && <div className='error'>{error.message}</div>}
        <button type='submit' disabled={loading || !stripe || !elements}>
          {loading ? 'Processing...' : 'Pay'}
        </button>
      </form>
    </div>
  );
};

const StripePaymentComponent: React.FC<StripePaymentComponentProps> = ({ amount, currency }) => {
  const [clientSecret, setClientSecret] = useState<string | null>(null);

  useEffect(() => {
    const fetchClientSecret = async () => {
      try {
        const token = sessionStorage.getItem('token');
        if (!token) throw new Error('Token not found');

        const stripePaymentUrl = BASE_URL_CONTEXT + '/stripe/create-payment-intent';
        const response = await axios.post(
          stripePaymentUrl,
          {
            amount: 1000, // Replace with actual amount
            currency: 'usd', // Replace with actual currency
          },
          {
            headers: {
              'X-Litemall-Token': token,
            },
          }
        );

        const { clientSecret } = response.data;
        console.log('Client secret:', clientSecret);
        if (!clientSecret) throw new Error('Client secret not found');
        setClientSecret(defaultSecret);
      } catch (error) {
        console.error('Error fetching client secret:', error);
      }
    };

    fetchClientSecret();
  }, []);

  if (!clientSecret) {
    return <div>Loading..</div>;
  }
  return (
    <Elements stripe={stripePromise} options={{ clientSecret: defaultSecret }}>
      <StripePaymentForm />;
    </Elements>
  );
};
export default StripePaymentComponent;
