import { beforeEach, describe, expect, it, jest } from '@jest/globals';
import type { Mock } from 'jest-mock';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import * as React from 'react';

jest.mock('app/shared/reducers/private/services/adminOrderCjApi', () => {
  const actual = jest.requireActual('app/shared/reducers/private/services/adminOrderCjApi') as Record<string, unknown>;
  return { ...actual, useDeleteOrderMutation: jest.fn() };
});

import { deleteConfirmMatches, orderDeleteTier } from 'app/shared/model/admin/order.model';
import * as cjApi from 'app/shared/reducers/private/services/adminOrderCjApi';
import DeleteOrderConfirm from './DeleteOrderConfirm';

type AnyMock = Mock<any>;
const useDelete = cjApi.useDeleteOrderMutation as unknown as AnyMock;

let deleteFn: AnyMock;
let onDeleted: AnyMock;

beforeEach(() => {
  useDelete.mockReset();
  deleteFn = jest.fn(() => Promise.resolve({ data: { errno: 0, errmsg: 'success', data: { status: 'DELETED' } } }));
  onDeleted = jest.fn();
  useDelete.mockReturnValue([deleteFn, { isLoading: false }]);
});

const SN = '20260802000007';

describe('orderDeleteTier', () => {
  it('offers the plain delete on finished orders', () => {
    for (const orderStatus of [102, 103, 203, 401, 402]) expect(orderDeleteTier({ orderStatus })).toBe('finished');
  });

  it('offers the guarded delete on paid orders never sent to CJ', () => {
    expect(orderDeleteTier({ orderStatus: 201 })).toBe('paid');
    expect(orderDeleteTier({ orderStatus: 202, cjOrderId: '' })).toBe('paid');
  });

  it('offers nothing for unpaid, shipped, CJ-live or unknown orders', () => {
    expect(orderDeleteTier({ orderStatus: 101 })).toBe('none');
    expect(orderDeleteTier({ orderStatus: 301 })).toBe('none');
    expect(orderDeleteTier({ orderStatus: 201, cjOrderId: 'CJ123' })).toBe('none');
    expect(orderDeleteTier({})).toBe('none');
    expect(orderDeleteTier(undefined)).toBe('none');
  });

  it('matches the typed order number exactly', () => {
    expect(deleteConfirmMatches(SN, ` ${SN} `)).toBe(true);
    expect(deleteConfirmMatches(SN, '2026080200000')).toBe(false);
    expect(deleteConfirmMatches(undefined, '')).toBe(false);
  });
});

describe('DeleteOrderConfirm', () => {
  it('deletes a finished order on one confirm, with no order number sent', async () => {
    render(<DeleteOrderConfirm order={{ id: 8, orderSn: SN, orderStatus: 102, actualPrice: '12.00' }} onDeleted={onDeleted} onCancel={() => undefined} />);
    expect(screen.queryByLabelText(/Type the order number/)).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delete' }));
    await waitFor(() => expect(onDeleted.mock.calls.length).toBe(1));
    expect(deleteFn.mock.calls[0][0]).toEqual({ orderId: 8, confirmOrderSn: undefined });
    expect(onDeleted.mock.calls[0][0]).toBe(`Order ${SN} deleted.`);
  });

  it('keeps a paid order locked until its number is typed, then sends it', async () => {
    render(<DeleteOrderConfirm order={{ id: 7, orderSn: SN, orderStatus: 201, actualPrice: '38.38' }} onDeleted={onDeleted} onCancel={() => undefined} />);
    expect(screen.getByText(/does not refund the customer/)).toBeTruthy();
    const confirm = screen.getByRole('button', { name: 'Confirm delete' }) as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText(/Type the order number/), { target: { value: SN } });
    expect(confirm.disabled).toBe(false);
    fireEvent.click(confirm);
    await waitFor(() => expect(onDeleted.mock.calls.length).toBe(1));
    expect(deleteFn.mock.calls[0][0]).toEqual({ orderId: 7, confirmOrderSn: SN });
  });

  it('shows a refusal in the server words and stays open', async () => {
    const refusal = '[QUEUED_FOR_CJ] order 7 is queued for CJ placement and could be sent at any moment — it cannot be deleted';
    deleteFn.mockImplementation(() => Promise.resolve({ error: { status: 422, data: { errno: 422, errmsg: refusal } } }));
    render(<DeleteOrderConfirm order={{ id: 7, orderSn: SN, orderStatus: 102 }} onDeleted={onDeleted} onCancel={() => undefined} />);
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delete' }));
    await waitFor(() => expect(screen.getByText(refusal)).toBeTruthy());
    expect(onDeleted.mock.calls.length).toBe(0);
  });
});
