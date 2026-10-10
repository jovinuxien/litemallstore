import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

/**
 * The standalone pay page ("Pay now" from the order list / order page / failed-payment
 * page). It used to offer card payment with no Stripe form mounted, so every card attempt
 * was rejected before it reached the server. These tests pin the real sequence and every
 * hand-over the order lifecycle contract §1 names.
 */
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn(), post: jest.fn() },
}));

let mockPublishableKey: string | null = 'pk_test_1';
jest.mock('app/shared/config/siteConfig', () => ({
  loadSiteConfig: jest.fn(() => Promise.resolve({ stripePublishableKey: mockPublishableKey })),
}));

jest.mock('app/shared/payment/stripeLoader', () => ({
  stripePromiseFor: jest.fn(() => Promise.resolve(null)),
  STRIPE_ELEMENTS_CURRENCY: 'eur',
}));

// What <Elements> was mounted with — the point of minting first is that this is the
// server's amount and currency, not a client guess.
const mockElementsOptions = jest.fn();
jest.mock('@stripe/react-stripe-js', () => ({
  Elements: ({ options, children }: { options: unknown; children: React.ReactNode }) => {
    mockElementsOptions(options);
    return <div data-testid='elements'>{children}</div>;
  },
}));

const mockValidate = jest.fn();
const mockConfirm = jest.fn();
jest.mock('app/shared/payment/StripeCardForm', () => {
  const ReactActual = jest.requireActual('react');
  const Form = ReactActual.forwardRef((_props: unknown, ref: unknown) => {
    ReactActual.useImperativeHandle(ref, () => ({ validate: mockValidate, confirm: mockConfirm }));
    return <div data-testid='card-form' />;
  });
  return { __esModule: true, default: Form };
});

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));

import { baseAxios } from 'app/config/axiosinstance';
import store from 'app/config/store';

import Payment from './Payment';

const mockGet = baseAxios.get as jest.Mock;
const mockPost = baseAxios.post as jest.Mock;

const unpaid = { id: 77, orderSn: '20260901-77', actualPrice: 54.31, handleOption: { pay: true }, orderStatusText: 'Unpaid' };
const envelope = (data: unknown) => Promise.resolve({ data: { errno: 0, data } });
const refuse = (status: number, message: string) => Promise.reject({ response: { status, data: { success: false, message } } });

/** Route the two POSTs the page makes. */
const onPost = (handlers: { intent?: () => Promise<unknown>; pay?: () => Promise<unknown> }) =>
  mockPost.mockImplementation((url: string) => {
    if (url.endsWith('/actions/payment-intent'))
      return (handlers.intent ?? (() => envelope({ clientSecret: 'cs_77', amount: 5431, currency: 'EUR' })))();
    if (url.endsWith('/actions/pay')) return (handlers.pay ?? (() => Promise.resolve({ data: { success: true, orderSn: unpaid.orderSn } })))();
    return Promise.reject(new Error(`unexpected POST ${url}`));
  });

const calls = (suffix: string) => mockPost.mock.calls.filter(([url]) => String(url).endsWith(suffix));

const flush = async () => {
  await act(async () => {
    for (let i = 0; i < 8; i += 1) await Promise.resolve();
  });
};

const mount = async () => {
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/pay/77']}>
        <Routes>
          <Route path='/pay/:orderId' element={<Payment />} />
        </Routes>
      </MemoryRouter>
    </Provider>
  );
  await flush();
};

const payNow = async () => {
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /Pay now/ }));
  });
  await flush();
};
const payButton = () => screen.getByRole('button', { name: /Pay now/ }) as HTMLButtonElement;

beforeEach(() => {
  mockPublishableKey = 'pk_test_1';
  [mockGet, mockPost, mockNavigate, mockValidate, mockConfirm, mockElementsOptions].forEach(m => m.mockReset());
  mockGet.mockImplementation(() => envelope(unpaid));
  mockValidate.mockResolvedValue(true);
  mockConfirm.mockResolvedValue({ kind: 'succeeded', paymentIntentId: 'pi_77' });
  onPost({});
});

describe('Payment (standalone pay page)', () => {
  it('mounts the card form with the SERVER intent amount and currency, minted once', async () => {
    await mount();
    expect(screen.getByTestId('card-form')).toBeTruthy();
    expect(calls('/actions/payment-intent')).toHaveLength(1);
    expect(mockElementsOptions).toHaveBeenLastCalledWith({ mode: 'payment', amount: 5431, currency: 'eur' });
    // The amount shown is the order's own, read from the server.
    expect(screen.getAllByText(/54\.31/).length).toBeGreaterThan(0);
    expect(screen.getByText('20260901-77')).toBeTruthy();
  });

  it('card: validates, confirms the minted intent, then reports the REAL intent id', async () => {
    await mount();
    await payNow();
    expect(mockValidate).toHaveBeenCalledTimes(1);
    expect(mockConfirm).toHaveBeenCalledWith('cs_77', '77');
    const [[, body]] = calls('/actions/pay');
    expect(body).toMatchObject({ paymentIntentId: 'pi_77' });
    expect(mockNavigate).toHaveBeenCalledWith('/pay/77/status?status=success');
  });

  it('card fields invalid ⇒ nothing is confirmed and nothing is posted', async () => {
    mockValidate.mockResolvedValue(false);
    await mount();
    await payNow();
    expect(mockConfirm).not.toHaveBeenCalled();
    expect(calls('/actions/pay')).toHaveLength(0);
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('a declined card stays on the page and can be retried against the SAME intent', async () => {
    mockConfirm.mockResolvedValueOnce({ kind: 'failed' });
    await mount();
    await payNow();
    expect(calls('/actions/pay')).toHaveLength(0);
    expect(mockNavigate).not.toHaveBeenCalled();
    await payNow();
    expect(calls('/actions/payment-intent')).toHaveLength(1);
    expect(mockConfirm).toHaveBeenCalledTimes(2);
    expect(calls('/actions/pay')).toHaveLength(1);
  });

  it('a bank debit in flight hands over to the status page and reports no payment', async () => {
    mockConfirm.mockResolvedValue({ kind: 'processing', paymentIntentId: 'pi_sepa' });
    await mount();
    await payNow();
    expect(calls('/actions/pay')).toHaveLength(0);
    expect(mockNavigate).toHaveBeenCalledWith('/pay/77/status?status=processing');
  });

  it('never asks Stripe twice for money already taken: a failed report re-sends the same id', async () => {
    let attempts = 0;
    onPost({
      pay() {
        attempts += 1;
        return attempts === 1 ? refuse(500, 'Order service is unavailable') : Promise.resolve({ data: { success: true } });
      },
    });
    await mount();
    await payNow();
    expect(screen.getByText('Order service is unavailable')).toBeTruthy(); // the server's words, verbatim
    expect(mockNavigate).not.toHaveBeenCalled();

    await payNow();
    expect(mockConfirm).toHaveBeenCalledTimes(1);
    expect(calls('/actions/pay').map(([, body]) => body.paymentIntentId)).toEqual(['pi_77', 'pi_77']);
    expect(mockNavigate).toHaveBeenCalledWith('/pay/77/status?status=success');
  });

  it('"already been paid" at pay time is success, not an error', async () => {
    onPost({ pay: () => refuse(402, 'Order 77 has already been paid') });
    await mount();
    await payNow();
    expect(mockNavigate).toHaveBeenCalledWith('/pay/77/status');
    expect(screen.queryByText(/already been paid/)).toBeNull();
  });

  it('an intent refused because the order is already paid goes straight to the status page', async () => {
    onPost({ intent: () => refuse(402, 'Order 77 has already been paid') });
    await mount();
    expect(mockNavigate).toHaveBeenCalledWith('/pay/77/status', { replace: true });
    expect(screen.queryByTestId('card-form')).toBeNull();
  });

  it('an intent refused because a bank debit is settling offers no second payment', async () => {
    onPost({ intent: () => refuse(402, 'A payment for this order is still being processed by your bank') });
    await mount();
    expect(mockNavigate).toHaveBeenCalledWith('/pay/77/status?status=processing', { replace: true });
  });

  it('any other intent refusal is shown verbatim, blocks card pay, and can be retried', async () => {
    let attempts = 0;
    onPost({
      intent() {
        attempts += 1;
        return attempts === 1 ? refuse(503, 'Card payments are not enabled') : envelope({ clientSecret: 'cs_77', amount: 5431, currency: 'eur' });
      },
    });
    await mount();
    expect(screen.getByText(/Card payments are not enabled/)).toBeTruthy();
    expect(screen.queryByTestId('card-form')).toBeNull();
    expect(payButton().disabled).toBe(true);

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    });
    await flush();
    expect(screen.getByTestId('card-form')).toBeTruthy();
    expect(payButton().disabled).toBe(false);
  });

  it('no Stripe key ⇒ card is unavailable, no intent is minted, and wallet still pays', async () => {
    mockPublishableKey = null;
    await mount();
    expect((document.getElementById('pm-card') as HTMLInputElement).disabled).toBe(true);
    expect((document.getElementById('pm-wallet') as HTMLInputElement).checked).toBe(true);
    expect(calls('/actions/payment-intent')).toHaveLength(0);

    await payNow();
    const [[, body]] = calls('/actions/pay');
    expect(body.paymentIntentId).toBeUndefined();
    expect(mockNavigate).toHaveBeenCalledWith('/pay/77/status?status=success');
  });

  it('a wallet refusal is shown in the server words', async () => {
    onPost({ pay: () => refuse(402, 'Insufficient wallet balance') });
    await mount();
    await act(async () => {
      fireEvent.click(document.getElementById('pm-wallet') as HTMLInputElement);
    });
    await payNow();
    expect(screen.getByText('Insufficient wallet balance')).toBeTruthy();
    expect(mockConfirm).not.toHaveBeenCalled();
  });

  it('an order that is already paid or cancelled gets no payment form', async () => {
    mockGet.mockImplementation(() => envelope({ id: 77, handleOption: { pay: false }, orderStatusText: 'Paid' }));
    await mount();
    expect(mockNavigate).toHaveBeenCalledWith('/pay/77/status', { replace: true });
    expect(calls('/actions/payment-intent')).toHaveLength(0);
    expect(screen.queryByRole('button', { name: /Pay now/ })).toBeNull();
  });

  it('an order that cannot be loaded says so and offers the order list, not a payment', async () => {
    mockGet.mockImplementation(() => Promise.reject(new Error('network')));
    await mount();
    expect(screen.getByText(/could not load this order/i)).toBeTruthy();
    expect(calls('/actions/payment-intent')).toHaveLength(0);
    expect(screen.queryByRole('button', { name: /Pay now/ })).toBeNull();
  });
});
