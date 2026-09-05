import React from 'react';
import { act, render, screen } from '@testing-library/react';

/**
 * Order lifecycle contract §1: `processing` (SEPA/bank debit) is NOT "your payment was
 * not completed, try another card" — it is in flight and will settle. And redirect
 * methods must come back to the pay-status page, not to the orders list.
 */
const mockConfirmPayment = jest.fn();
jest.mock('@stripe/react-stripe-js', () => ({
  PaymentElement: () => <div data-testid='payment-element' />,
  useStripe: () => ({ confirmPayment: mockConfirmPayment }),
  useElements: () => ({ submit: jest.fn().mockResolvedValue({}) }),
}));

import StripeCardForm, { paymentReturnUrl, StripeCardHandle } from './StripeCardForm';

const mount = () => {
  const ref = React.createRef<StripeCardHandle>();
  render(<StripeCardForm ref={ref} />);
  return ref;
};

beforeEach(() => mockConfirmPayment.mockReset());

describe('StripeCardForm.confirm', () => {
  it('succeeded ⇒ the intent id, nothing rendered', async () => {
    mockConfirmPayment.mockResolvedValue({ paymentIntent: { id: 'pi_ok', status: 'succeeded' } });
    const ref = mount();
    let outcome;
    await act(async () => {
      outcome = await ref.current!.confirm('cs_1', 41);
    });
    expect(outcome).toEqual({ kind: 'succeeded', paymentIntentId: 'pi_ok' });
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('processing ⇒ an informational notice that says do not pay again, and NOT the try-another-card error', async () => {
    mockConfirmPayment.mockResolvedValue({ paymentIntent: { id: 'pi_sepa', status: 'processing' } });
    const ref = mount();
    let outcome;
    await act(async () => {
      outcome = await ref.current!.confirm('cs_1', 41);
    });
    expect(outcome).toEqual({ kind: 'processing', paymentIntentId: 'pi_sepa' });
    expect(screen.getByText(/Your bank is processing this payment/)).toBeTruthy();
    expect(screen.getByText(/do not pay again/)).toBeTruthy();
    expect(screen.queryByText(/try another card/i)).toBeNull();
    expect(screen.getByRole('alert').className).toContain('alert-info');
  });

  it('any other unsucceeded status is still refused as not completed', async () => {
    mockConfirmPayment.mockResolvedValue({ paymentIntent: { id: 'pi_x', status: 'requires_payment_method' } });
    const ref = mount();
    let outcome;
    await act(async () => {
      outcome = await ref.current!.confirm('cs_1', 41);
    });
    expect(outcome).toEqual({ kind: 'failed' });
    expect(screen.getByText(/was not completed/)).toBeTruthy();
  });

  it('redirect methods return to the pay-status page for THIS order', async () => {
    mockConfirmPayment.mockResolvedValue({ paymentIntent: { id: 'pi_ok', status: 'succeeded' } });
    const ref = mount();
    await act(async () => {
      await ref.current!.confirm('cs_1', 41);
    });
    const args = mockConfirmPayment.mock.calls[0][0];
    expect(args.confirmParams.return_url).toBe(`${window.location.origin}/pay/41/status`);
    expect(args.redirect).toBe('if_required');
    expect(paymentReturnUrl(41)).toMatch(/\/pay\/41\/status$/);
  });
});
