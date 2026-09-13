import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

/**
 * Order lifecycle contract §3 — the 202 dead end. A refund request in progress used to be
 * filtered OUT of the Refunds page (it satisfies neither `aftersaleStatus > 0` nor
 * `handleOption.refund`); now it is listed with a "Withdraw refund request" action, and a
 * refusal shows the server's reason verbatim.
 */
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn(), post: jest.fn() },
}));

import { baseAxios } from 'app/config/axiosinstance';

import RefundList, { isRefundRow } from './RefundList';

const mockGet = baseAxios.get as jest.Mock;
const mockPost = baseAxios.post as jest.Mock;

const inProgress = { id: 9, orderSn: 'SN9', orderStatusText: 'REFUND IN PROGRESS', actualPrice: 12.5, handleOption: { withdrawRefund: true }, goodsList: [] };
const paid = { id: 10, orderSn: 'SN10', orderStatusText: 'PAID', actualPrice: 3, handleOption: { refund: true }, goodsList: [] };
const shipped = { id: 11, orderSn: 'SN11', orderStatusText: 'SHIPPED', actualPrice: 3, handleOption: { confirm: true }, goodsList: [] };
const aftersale = { id: 12, orderSn: 'SN12', orderStatusText: 'DELIVERED', actualPrice: 3, aftersaleStatus: 1, handleOption: {}, goodsList: [] };

const list = (rows: unknown[]) => Promise.resolve({ data: { errno: 0, data: { list: rows, total: rows.length } } });

const mount = () =>
  render(
    <MemoryRouter>
      <RefundList />
    </MemoryRouter>
  );

beforeEach(() => {
  mockGet.mockReset();
  mockPost.mockReset();
});

describe('isRefundRow', () => {
  it('keeps the old cases and adds the 202 case', () => {
    expect(isRefundRow(inProgress)).toBe(true);
    expect(isRefundRow(paid)).toBe(true);
    expect(isRefundRow(aftersale)).toBe(true);
    expect(isRefundRow(shipped)).toBe(false);
  });
});

describe('RefundList', () => {
  it('lists a refund-in-progress order with a withdraw action', async () => {
    mockGet.mockImplementation(() => list([inProgress, shipped, aftersale]));
    mount();
    await waitFor(() => expect(screen.getByText('#SN9')).toBeTruthy());
    expect(screen.getByText('#SN12')).toBeTruthy();
    expect(screen.queryByText('#SN11')).toBeNull();
    // Only the 202 row offers the withdraw action.
    expect(screen.getAllByText('Withdraw refund request')).toHaveLength(1);
  });

  it('withdraw posts to the contract endpoint and reloads', async () => {
    mockGet.mockImplementationOnce(() => list([inProgress])).mockImplementationOnce(() => list([{ ...inProgress, orderStatusText: 'PAID', handleOption: { refund: true } }]));
    mockPost.mockImplementation(() => Promise.resolve({ data: { success: true, currentStatus: 'PAID' } }));
    mount();
    await waitFor(() => expect(screen.getByText('Withdraw refund request')).toBeTruthy());
    fireEvent.click(screen.getByText('Withdraw refund request'));
    await waitFor(() => expect(mockPost).toHaveBeenCalled());
    expect(mockPost.mock.calls[0][0]).toBe('/srv/order/9/actions/refund/withdraw');
    await waitFor(() => expect(screen.getByText('PAID')).toBeTruthy());
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('a typed refusal is shown verbatim, and the row stays', async () => {
    mockGet.mockImplementation(() => list([inProgress]));
    const refusal = 'Refund request for order 9 can no longer be withdrawn: it has already been decided.';
    mockPost.mockImplementation(() =>
      Promise.reject({ response: { status: 422, data: { success: false, message: refusal, errorCode: 'INVALID_STATE_TRANSITION' } } })
    );
    mount();
    await waitFor(() => expect(screen.getByText('Withdraw refund request')).toBeTruthy());
    fireEvent.click(screen.getByText('Withdraw refund request'));
    await waitFor(() => expect(screen.getByRole('alert').textContent).toBe(refusal));
    expect(screen.getByText('#SN9')).toBeTruthy();
  });

  it('a 2xx with success:false is a refusal too', async () => {
    mockGet.mockImplementation(() => list([inProgress]));
    mockPost.mockImplementation(() => Promise.resolve({ data: { success: false, message: 'Order not found' } }));
    mount();
    await waitFor(() => expect(screen.getByText('Withdraw refund request')).toBeTruthy());
    fireEvent.click(screen.getByText('Withdraw refund request'));
    await waitFor(() => expect(screen.getByRole('alert').textContent).toBe('Order not found'));
  });
});
