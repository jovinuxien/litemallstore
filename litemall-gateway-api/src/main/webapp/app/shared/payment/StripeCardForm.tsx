import { PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js';
import { t } from 'app/i18n';
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
 *
 * <p><b>Three outcomes, not two</b> (order lifecycle contract §1). `succeeded` is the card
 * case. `processing` is a bank debit (SEPA and friends): the debit is in flight for days
 * and WILL settle, the webhook marks the order paid when it does, and the backend refuses
 * to mint a second intent meanwhile — so it is rendered as information, never as a
 * failure, and the caller must not offer a retry. Redirect methods (Klarna, iDEAL,
 * Bancontact, 3-DS) leave the page and come back to `/pay/:orderId/status`, which polls
 * the order until the webhook has settled it.
 */

export type StripeConfirmOutcome =
  | { kind: 'succeeded'; paymentIntentId: string }
  | { kind: 'processing'; paymentIntentId: string }
  | { kind: 'failed' };

export interface StripeCardHandle {
  /** Validate the card fields. Call BEFORE placing the order; errors render inline. */
  validate: () => Promise<boolean>;
  /**
   * Confirm against a server-created intent for `orderId` (which only names the return
   * page for redirect methods). Resolves to the outcome; a failure has already rendered its
   * reason inline. Never throws.
   */
  confirm: (clientSecret: string, orderId: number | string) => Promise<StripeConfirmOutcome>;
}

/** Where a redirect-based payment method lands when it comes back from the bank. */
export const paymentReturnUrl = (orderId: number | string): string => `${window.location.origin}/pay/${orderId}/status`;

const StripeCardForm = React.forwardRef<StripeCardHandle, { disabled?: boolean }>(({ disabled }, ref) => {
  const stripe = useStripe();
  const elements = useElements();
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useImperativeHandle(ref, () => ({
    validate: async () => {
      if (!stripe || !elements) {
        setError(t('common:forms.card.loading'));
        return false;
      }
      setError(null);
      const { error: submitError } = await elements.submit();
      if (submitError) {
        setError(submitError.message ?? t('common:forms.card.checkDetails'));
        return false;
      }
      return true;
    },

    confirm: async (clientSecret: string, orderId: number | string) => {
      if (!stripe || !elements) {
        setError(t('common:forms.card.loading'));
        return { kind: 'failed' };
      }
      setError(null);
      setNotice(null);
      // redirect: 'if_required' keeps the customer here for plain cards while still
      // supporting 3-D Secure and redirect methods, which return to return_url — the
      // pay-status page, which waits for the webhook rather than trusting the redirect.
      const { error: confirmError, paymentIntent } = await stripe.confirmPayment({
        elements,
        clientSecret,
        redirect: 'if_required',
        confirmParams: { return_url: paymentReturnUrl(orderId) },
      });
      if (confirmError) {
        setError(confirmError.message ?? t('common:forms.card.notCharged'));
        return { kind: 'failed' };
      }
      if (paymentIntent?.status === 'succeeded') {
        return { kind: 'succeeded', paymentIntentId: paymentIntent.id };
      }
      if (paymentIntent?.status === 'processing') {
        // A bank debit in flight. Not a failure and not yet a payment: the server will mark
        // the order paid from the webhook once the debit settles, and it refuses a second
        // intent meanwhile. Say so, and say "do not pay again".
        setNotice(t('common:forms.card.processing'));
        return { kind: 'processing', paymentIntentId: paymentIntent.id };
      }
      // Never report an unsucceeded intent as payment. The server re-retrieves the intent
      // and asserts status/amount/currency/metadata.orderId, so it would reject this
      // anyway — and claiming success here is exactly the lie the stub told.
      setError(t('common:forms.card.notCompleted'));
      return { kind: 'failed' };
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
      {notice && (
        <Alert variant='info' className='mt-3 mb-0 py-2 small'>
          {notice}
        </Alert>
      )}
    </div>
  );
});

StripeCardForm.displayName = 'StripeCardForm';

export default StripeCardForm;
