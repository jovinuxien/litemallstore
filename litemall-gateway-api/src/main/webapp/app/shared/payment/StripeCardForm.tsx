import { PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js';
import React, { useImperativeHandle, useState } from 'react';
import { Alert } from 'react-bootstrap';

/**
 * Stripe Elements card capture (Wave-7 Task B).
 *
 * <p>Replaces the `pi_stub_${orderId}` placeholder `orderSlice` used to fabricate — and
 * the `Checkout.tsx` copy that disclosed the placeholder to the customer.
 *
 * <p>Card details are entered inside Stripe's iframe and never touch our JS, our servers
 * or our logs; we only ever see the resulting PaymentIntent id. That is the point of
 * Elements, and it is what keeps this out of PCI scope.
 *
 * <p><b>Two-phase by necessity — this is the deferred-intent flow.</b> Stripe requires
 * `elements.submit()` before an intent is confirmed, but we must not create the intent
 * (nor charge anything) until an order exists to attach it to. So the caller runs:
 * {@link StripeCardHandle#validate} → place the order → create the intent server-side →
 * {@link StripeCardHandle#confirm} with the returned clientSecret. Confirming first and
 * placing the order afterwards would risk a charge with no order behind it.
 */

export interface StripeCardHandle {
  /** Validate the card fields. Call BEFORE placing the order; errors render inline. */
  validate: () => Promise<boolean>;
  /**
   * Confirm against a server-created intent. Resolves to the PaymentIntent id, or null if
   * the payment did not succeed (the reason is rendered inline). Never throws.
   */
  confirm: (clientSecret: string) => Promise<string | null>;
}

const StripeCardForm = React.forwardRef<StripeCardHandle, { disabled?: boolean }>(({ disabled }, ref) => {
  const stripe = useStripe();
  const elements = useElements();
  const [error, setError] = useState<string | null>(null);

  useImperativeHandle(ref, () => ({
    validate: async () => {
      if (!stripe || !elements) {
        setError('Card payment is still loading — please try again in a moment.');
        return false;
      }
      setError(null);
      const { error: submitError } = await elements.submit();
      if (submitError) {
        setError(submitError.message ?? 'Please check your card details.');
        return false;
      }
      return true;
    },

    confirm: async (clientSecret: string) => {
      if (!stripe || !elements) {
        setError('Card payment is still loading — please try again in a moment.');
        return null;
      }
      setError(null);
      // redirect: 'if_required' keeps the customer here for plain cards while still
      // supporting 3-D Secure, which redirects and returns to return_url.
      const { error: confirmError, paymentIntent } = await stripe.confirmPayment({
        elements,
        clientSecret,
        redirect: 'if_required',
        confirmParams: { return_url: `${window.location.origin}/orders` },
      });
      if (confirmError) {
        setError(confirmError.message ?? 'Your card could not be charged.');
        return null;
      }
      if (paymentIntent?.status !== 'succeeded') {
        // Never report an unsucceeded intent as payment. The server re-retrieves the
        // intent and asserts status/amount/currency/metadata.orderId, so it would reject
        // this anyway — and claiming success here is exactly the lie the stub told.
        setError('Your payment was not completed. Please try another card.');
        return null;
      }
      return paymentIntent.id;
    },
  }));

  return (
    <div>
      <PaymentElement options={{ readOnly: disabled }} />
      {error && (
        <Alert variant='danger' className='mt-3 mb-0 py-2 small'>
          {error}
        </Alert>
      )}
    </div>
  );
});

StripeCardForm.displayName = 'StripeCardForm';

export default StripeCardForm;
