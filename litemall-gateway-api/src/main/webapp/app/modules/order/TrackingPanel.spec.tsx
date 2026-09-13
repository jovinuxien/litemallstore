import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';

/**
 * Order lifecycle contract §2: a SHIPPED order with no carrier number yet answers
 * `{shipped:true, status:"TRACKING_PENDING"}` and must read "shipped, number on its
 * way" — not "not shipped yet" beside a Shipped badge, and not the raw status code.
 */
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn(), post: jest.fn() },
}));

import { baseAxios } from 'app/config/axiosinstance';

import TrackingPanel from './TrackingPanel';

const mockGet = baseAxios.get as jest.Mock;
const envelope = (data: unknown) => Promise.resolve({ data: { errno: 0, data } });

beforeEach(() => mockGet.mockReset());

describe('TrackingPanel', () => {
  it('TRACKING_PENDING ⇒ shipped copy, carrier if known, no raw status, no not-shipped line', async () => {
    mockGet.mockImplementation(() => envelope({ shipped: true, status: 'TRACKING_PENDING', carrier: 'PostNL', events: [] }));
    render(<TrackingPanel orderId={5} />);
    await waitFor(() => expect(screen.getByText('Shipped — tracking number on its way')).toBeTruthy());
    expect(screen.getByText('PostNL')).toBeTruthy();
    expect(screen.queryByText('TRACKING_PENDING')).toBeNull();
    expect(screen.queryByText(/Not shipped yet/)).toBeNull();
    expect(screen.queryByText(/temporarily unavailable/)).toBeNull();
  });

  it('not shipped is unchanged', async () => {
    mockGet.mockImplementation(() => envelope({ shipped: false, status: 'NOT_SHIPPED', events: [] }));
    render(<TrackingPanel orderId={5} />);
    await waitFor(() => expect(screen.getByText(/Not shipped yet/)).toBeTruthy());
    expect(screen.queryByText(/tracking number on its way/)).toBeNull();
  });

  it('shipped with a number renders the carrier line and events as before', async () => {
    mockGet.mockImplementation(() =>
      envelope({
        shipped: true,
        status: 'IN_TRANSIT',
        carrier: 'CJPacket',
        trackNumber: 'CJ123',
        events: [{ time: '2026-09-01 10:00', description: 'Departed facility' }],
      })
    );
    render(<TrackingPanel orderId={5} />);
    await waitFor(() => expect(screen.getByText('CJ123')).toBeTruthy());
    expect(screen.getByText('Departed facility')).toBeTruthy();
    expect(screen.queryByText(/tracking number on its way/)).toBeNull();
  });
});
