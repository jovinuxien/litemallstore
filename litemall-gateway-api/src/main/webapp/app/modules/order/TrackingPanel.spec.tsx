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
    // An older backend sends no link and no contact: neither row appears.
    expect(screen.queryByText('Track with the carrier')).toBeNull();
    expect(screen.queryByText('Last-mile carrier contact')).toBeNull();
  });

  it('renders the hop history with places, the last-mile contact and the external link', async () => {
    mockGet.mockImplementation(() =>
      envelope({
        shipped: true,
        status: 'Arrived Courier Facility',
        carrier: 'CJPacket Euro Sensitive F',
        trackNumber: 'EQK861YQ',
        lastMileCarrier: 'CityMail',
        lastTrackNumber: 'BCMEQ861YQ',
        lastMileCarrierPhone: '0771-40 60 40',
        lastMileCarrierEmail: 'mottagarservice@citymail.se',
        trackingUrl: 'https://t.17track.net/en#nums=EQK861YQ',
        events: [
          { time: '2026-11-18 21:35:56', description: 'Released from customs', location: 'HEL' },
          { time: '2026-11-10 18:30:02', description: 'Label created' },
        ],
      })
    );
    render(<TrackingPanel orderId={11} />);
    await waitFor(() => expect(screen.getByText('Released from customs')).toBeTruthy());
    expect(screen.getByText('2026-11-18 21:35:56 · HEL')).toBeTruthy();
    expect(screen.getByText('2026-11-10 18:30:02')).toBeTruthy();
    expect(screen.getByText('CityMail (BCMEQ861YQ)')).toBeTruthy();
    expect((screen.getByText('0771-40 60 40') as HTMLAnchorElement).getAttribute('href')).toBe('tel:0771406040');
    expect((screen.getByText('mottagarservice@citymail.se') as HTMLAnchorElement).getAttribute('href')).toBe('mailto:mottagarservice@citymail.se');
    const link = screen.getByText('Track with the carrier').closest('a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('https://t.17track.net/en#nums=EQK861YQ');
    expect(link.getAttribute('rel')).toContain('noopener');
  });

  it('offers the external link even when the event history is unavailable, and never a non-http one', async () => {
    mockGet.mockImplementation(() => envelope({ shipped: true, status: null, trackNumber: 'EQK1', trackingUrl: 'https://t.17track.net/en#nums=EQK1', events: [] }));
    const first = render(<TrackingPanel orderId={11} />);
    await waitFor(() => expect(screen.getByText('Track with the carrier')).toBeTruthy());
    expect(screen.getByText(/temporarily unavailable/)).toBeTruthy();
    first.unmount();

    mockGet.mockImplementation(() => envelope({ shipped: true, trackNumber: 'EQK1', trackingUrl: 'javascript:alert(1)', events: [] }));
    render(<TrackingPanel orderId={12} />);
    await waitFor(() => expect(screen.getByText('EQK1')).toBeTruthy());
    expect(screen.queryByText('Track with the carrier')).toBeNull();
  });
});
