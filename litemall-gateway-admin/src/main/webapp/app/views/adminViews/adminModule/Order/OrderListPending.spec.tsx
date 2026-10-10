import { beforeEach, describe, expect, it, jest } from '@jest/globals';
import type { Mock } from 'jest-mock';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import * as React from 'react';
import { MemoryRouter } from 'react-router-dom';

// Pending-CJ-approval tab, lifecycle package B: a parked row renders distinctly
// with CJ's words, offers Requeue (not Approve), confirms inline quoting the park
// reason, and shows the server's answer — success or [NOT_PARKED] — verbatim.
// The RTK hooks are mocked at the module seam (SeoTitleList.spec precedent).
jest.mock('app/shared/reducers/private/services/adminCatalogApi', () => ({
  useListOrdersQuery: jest.fn(),
}));
jest.mock('app/shared/reducers/private/services/adminOrderCjApi', () => {
  const actual = jest.requireActual('app/shared/reducers/private/services/adminOrderCjApi') as Record<string, unknown>;
  return {
    ...actual,
    useGetCjPlacementPendingQuery: jest.fn(),
    useRequeueCjPlacementMutation: jest.fn(),
    downloadOrderExport: jest.fn(),
  };
});

import * as catalogApi from 'app/shared/reducers/private/services/adminCatalogApi';
import * as cjApi from 'app/shared/reducers/private/services/adminOrderCjApi';
import OrderList from './OrderList';

type AnyMock = Mock<any>;
const useList = catalogApi.useListOrdersQuery as unknown as AnyMock;
const usePending = cjApi.useGetCjPlacementPendingQuery as unknown as AnyMock;
const useRequeue = cjApi.useRequeueCjPlacementMutation as unknown as AnyMock;

const PARK_REASON = 'CJ rejected the fulfilment order: 7001: Please enter a IOSS number.';

const parkedRow: cjApi.IPendingCjRow = {
  orderId: 11,
  orderSn: '2026080711111',
  payTime: '2026-08-07T09:10:00',
  actualPrice: '8.90',
  consignee: 'Jane Doe',
  country: 'DE',
  items: [{ name: 'Spatula set', number: 1 }],
  cjReady: false,
  parked: true,
  parkReason: PARK_REASON,
  holdReason: `CJ rejected the placement: ${PARK_REASON} — fix the cause and use Requeue (approval alone does nothing)`,
};

const readyRow: cjApi.IPendingCjRow = {
  orderId: 12,
  orderSn: '2026080712222',
  payTime: '2026-08-07T09:20:00',
  actualPrice: '38.38',
  consignee: 'John Roe',
  country: 'US',
  items: [{ name: 'Garden hose', number: 2 }],
  cjReady: true,
  parked: false,
};

const renderPending = () =>
  render(
    <MemoryRouter initialEntries={['/admin/mall/order?tab=pending']}>
      <OrderList />
    </MemoryRouter>
  );

describe('OrderList pending tab — parked rows + Requeue', () => {
  let requeue: AnyMock;
  let refetch: AnyMock;

  beforeEach(() => {
    useList.mockReset();
    usePending.mockReset();
    useRequeue.mockReset();
    useList.mockReturnValue({ data: { list: [], total: 0, pages: 0 }, isLoading: false, isFetching: false, isError: false });
    refetch = jest.fn();
    usePending.mockReturnValue({
      data: { list: [parkedRow, readyRow], total: 2, pages: 1 },
      isLoading: false,
      isFetching: false,
      isError: false,
      refetch,
    });
    requeue = jest.fn();
    useRequeue.mockReturnValue([requeue, { isLoading: false }]);
  });

  it("renders the parked row distinctly, with the hold sentence (CJ's words) once, and Requeue only on that row", () => {
    renderPending();
    expect(screen.getByText('Parked')).toBeTruthy();
    expect(screen.getByText('Ready')).toBeTruthy();
    // holdReason already embeds parkReason — it must appear exactly once, not twice
    expect(screen.getAllByText(new RegExp('7001: Please enter a IOSS number')).length).toBe(1);
    expect(screen.getAllByText('Requeue').length).toBe(1);
    expect(screen.getAllByText('Review').length).toBe(2);
    expect(screen.queryByText(/Approve/)).toBeNull();
  });

  it("confirms inline quoting CJ's reason, then shows the success message and refetches the page", async () => {
    requeue.mockResolvedValue({ data: { errno: 0, data: { orderId: 11, status: 'REQUEUED', message: 'requeued for CJ placement' } } });
    renderPending();
    fireEvent.click(screen.getByText('Requeue'));
    const confirm = screen.getByTestId('requeue-confirm');
    expect(confirm.textContent).toContain('Retry sending order 2026080711111 to CJ? Fix the cause first');
    expect(confirm.textContent).toContain(`CJ said: ${PARK_REASON}`);
    expect(requeue).not.toHaveBeenCalled(); // opening the confirm is not the action

    fireEvent.click(screen.getByText('Confirm requeue'));
    await waitFor(() => expect(requeue).toHaveBeenCalledWith(11));
    await waitFor(() => expect(screen.getByText('2026080711111: requeued for CJ placement')).toBeTruthy());
    expect(refetch).toHaveBeenCalledTimes(1);
    expect(screen.queryByTestId('requeue-confirm')).toBeNull();
  });

  it('shows a [NOT_PARKED] refusal verbatim and keeps the confirm open, without refetching', async () => {
    requeue.mockResolvedValue({ error: { status: 422, data: { errno: 422, errmsg: '[NOT_PARKED] order 11 is not parked' } } });
    renderPending();
    fireEvent.click(screen.getByText('Requeue'));
    fireEvent.click(screen.getByText('Confirm requeue'));
    await waitFor(() => expect(screen.getByText('2026080711111: [NOT_PARKED] order 11 is not parked')).toBeTruthy());
    expect(refetch).not.toHaveBeenCalled();
    expect(screen.getByTestId('requeue-confirm')).toBeTruthy();
  });

  it('Cancel closes the confirm without calling the endpoint', () => {
    renderPending();
    fireEvent.click(screen.getByText('Requeue'));
    fireEvent.click(screen.getByText('Cancel'));
    expect(screen.queryByTestId('requeue-confirm')).toBeNull();
    expect(requeue).not.toHaveBeenCalled();
  });
});
