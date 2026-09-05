import React from 'react';
import { act, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

/**
 * Order lifecycle contract §1: the pay-status page is the Stripe return_url and it WAITS
 * for the webhook. It never trusts `redirect_status=succeeded`, it never renders a
 * `processing` bank debit as a failure, and a failed redirect offers a retry.
 */
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn(), post: jest.fn() },
}));

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));

import { baseAxios } from 'app/config/axiosinstance';

import PaymentStatus, { MAX_POLLS, POLL_MS } from './PaymentStatus';

const mockGet = baseAxios.get as jest.Mock;

const detail = (handleOption: Record<string, boolean>, orderStatusText: string) =>
  Promise.resolve({ data: { errno: 0, data: { id: 77, handleOption, orderStatusText } } });

const renderAt = (search: string) =>
  render(
    <MemoryRouter initialEntries={[`/pay/77/status${search}`]}>
      <Routes>
        <Route path='/pay/:orderId/status' element={<PaymentStatus />} />
      </Routes>
    </MemoryRouter>
  );

const flush = async () => {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
};

const phase = () => document.querySelector('[data-phase]')?.getAttribute('data-phase');

beforeEach(() => {
  jest.useFakeTimers();
  mockGet.mockReset();
  mockNavigate.mockReset();
});
afterEach(() => {
  jest.useRealTimers();
});

describe('PaymentStatus', () => {
  it('a succeeded redirect still asks the order service, and settles only when the order is no longer payable', async () => {
    mockGet
      .mockImplementationOnce(() => detail({ pay: true, cancel: true }, 'UNPAID'))
      .mockImplementationOnce(() => detail({ refund: true }, 'PAID'));
    renderAt('?redirect_status=succeeded&payment_intent=pi_1');
    await flush();
    expect(phase()).toBe('confirming');
    expect(screen.getByText('Confirming your payment…')).toBeTruthy();

    await act(async () => {
      jest.advanceTimersByTime(POLL_MS);
    });
    await flush();
    expect(phase()).toBe('paid');
    expect(screen.getByText('Payment successful')).toBeTruthy();
    expect(mockGet).toHaveBeenCalledTimes(2);
    expect(mockGet.mock.calls[0][0]).toContain('/order/detail?orderId=77');

    // Settled ⇒ the order page, not the list.
    await act(async () => {
      jest.advanceTimersByTime(3000);
    });
    expect(mockNavigate).toHaveBeenCalledWith('/order/77');
  });

  it('a processing bank debit is information, not a failure — and says do not pay again', async () => {
    mockGet.mockImplementation(() => detail({ pay: true, cancel: true }, 'UNPAID'));
    renderAt('?status=processing');
    await flush();
    expect(phase()).toBe('processing');
    expect(screen.getByText('Your bank is processing this payment')).toBeTruthy();
    expect(screen.getByText(/do not pay again/)).toBeTruthy();
    expect(screen.queryByText('Try again')).toBeNull();
    expect(screen.queryByText('Payment failed')).toBeNull();

    // Even after the polling budget it stays "processing" — the bank really is slow.
    for (let i = 0; i < MAX_POLLS + 1; i += 1) {
      // eslint-disable-next-line no-await-in-loop
      await act(async () => {
        jest.advanceTimersByTime(POLL_MS);
      });
      // eslint-disable-next-line no-await-in-loop
      await flush();
    }
    expect(phase()).toBe('processing');
    expect(mockGet).toHaveBeenCalledTimes(MAX_POLLS);
  });

  it('a failed redirect is terminal without polling and offers a retry on the same order', async () => {
    renderAt('?redirect_status=failed');
    await flush();
    expect(phase()).toBe('failed');
    expect(screen.getByText('Payment failed')).toBeTruthy();
    expect(screen.getByText('Try again').getAttribute('href')).toBe('/pay/77');
    expect(mockGet).not.toHaveBeenCalled();
  });

  it('a cancelled order is reported as cancelled, never as paid', async () => {
    mockGet.mockImplementation(() => detail({ delete: true, rebuy: true }, 'CANCELLED'));
    renderAt('?status=success');
    await flush();
    expect(phase()).toBe('cancelled');
    expect(screen.getByText('This order was cancelled')).toBeTruthy();
  });

  it('a plain confirmation that never lands turns into "still waiting", not a claim', async () => {
    mockGet.mockImplementation(() => Promise.reject(new Error('network')));
    renderAt('?redirect_status=succeeded');
    await flush();
    for (let i = 0; i < MAX_POLLS; i += 1) {
      // eslint-disable-next-line no-await-in-loop
      await act(async () => {
        jest.advanceTimersByTime(POLL_MS);
      });
      // eslint-disable-next-line no-await-in-loop
      await flush();
    }
    expect(phase()).toBe('timeout');
    expect(screen.getByText('Still waiting for confirmation')).toBeTruthy();
    expect(screen.queryByText('Payment successful')).toBeNull();
  });
});
