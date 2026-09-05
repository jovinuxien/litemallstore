import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';

jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn(), post: jest.fn() },
}));

import { baseAxios } from 'app/config/axiosinstance';

import OrderTimelinePanel from './OrderTimelinePanel';

const mockGet = baseAxios.get as jest.Mock;
const envelope = (data: unknown) => Promise.resolve({ data: { errno: 0, data } });

beforeEach(() => mockGet.mockReset());

describe('OrderTimelinePanel', () => {
  it('renders the trail oldest-first as sentences with time and actor, from the contract endpoint', async () => {
    mockGet.mockImplementation(() =>
      envelope([
        { fromStatus: null, toStatus: 101, toStatusText: 'UNPAID', changeType: 'create', changeMessage: 'Order created', operator: 'user', changeTime: '2026-09-01T10:00:00' },
        { fromStatus: 101, toStatus: 201, toStatusText: 'PAID', changeType: 'pay', changeMessage: 'Payment received', operator: 'user', changeTime: [2026, 9, 1, 10, 5, 0] },
        { fromStatus: 201, toStatus: 201, toStatusText: 'PAID', changeType: 'cj_placement', changeMessage: 'Approved for fulfilment', operator: 'admin:1', changeTime: '2026-09-02T08:00:00' },
      ])
    );
    render(<OrderTimelinePanel orderId={77} />);
    await waitFor(() => expect(screen.getByText('Order placed')).toBeTruthy());
    expect(screen.getByText('Order timeline')).toBeTruthy();
    expect(mockGet.mock.calls[0][0]).toBe('/srv/order/77/timeline');
    const items = document.querySelectorAll('li');
    expect(items).toHaveLength(3);
    expect(items[0].textContent).toContain('Order placed');
    expect(items[0].textContent).toContain('Order created');
    expect(items[0].textContent).toContain('2026-09-01 10:00');
    expect(items[1].textContent).toContain('Payment received');
    expect(items[1].textContent).toContain('2026-09-01 10:05'); // tuple timestamp
    expect(items[2].textContent).toContain('Fulfilment update');
    expect(items[2].textContent).toContain('Approved for fulfilment');
    expect(items[2].textContent).toContain('Our team');
    expect(items[2].textContent).not.toContain('admin:1');
  });

  it('no entries ⇒ no section at all (never an empty heading)', async () => {
    mockGet.mockImplementation(() => envelope([]));
    const { container } = render(<OrderTimelinePanel orderId={77} />);
    await waitFor(() => expect(mockGet).toHaveBeenCalled());
    await waitFor(() => expect(container.textContent).toBe(''));
  });

  it('404 hides the section; another failure shows the unavailable note', async () => {
    mockGet.mockImplementation(() => Promise.resolve({ data: { errno: 404, errmsg: 'Order not found' } }));
    const { container, unmount } = render(<OrderTimelinePanel orderId={77} />);
    await waitFor(() => expect(mockGet).toHaveBeenCalled());
    await waitFor(() => expect(container.textContent).toBe(''));
    unmount();

    mockGet.mockImplementation(() => Promise.reject(new Error('network')));
    render(<OrderTimelinePanel orderId={78} />);
    await waitFor(() => expect(screen.getByText('The timeline is temporarily unavailable.')).toBeTruthy());
  });
});
