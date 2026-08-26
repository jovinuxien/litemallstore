import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';

import { baseAxios } from 'app/config/axiosinstance';

import GoodsListPage from './GoodsListPage';

/**
 * "Today's Deals" used to name TWO different pages: the header strip pointed it
 * at /deals (the real deal_flag markdown surface) while the "☰ All" drawer
 * pointed the identical label at /hot, and /hot's own heading agreed with the
 * drawer. /hot ranks by listed_num — popularity, not markdown — so the label
 * was both ambiguous and wrong about what the page does.
 *
 * This pins the page heading. The two link labels live in Layout and
 * CategoryDrawer; what matters for both is that nothing but /deals claims the
 * name, so the negative assertion below is the load-bearing one.
 */
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn() },
}));

const mockGet = baseAxios.get as jest.Mock;

beforeEach(() => {
  mockGet.mockReset();
  mockGet.mockResolvedValue({ data: { errno: 0, data: { goodsList: [], total: 0 } } });
});

describe('flat listing page names', () => {
  it('titles /hot for what it ranks, and never "Today’s Deals"', async () => {
    render(<GoodsListPage mode='hot' />);

    expect(await screen.findByText('Best sellers')).toBeTruthy();
    expect(screen.queryByText(/Today.s Deals/i)).toBeNull();
  });

  it('still sorts /hot by popularity, not by discount', async () => {
    render(<GoodsListPage mode='hot' />);

    await waitFor(() => expect(mockGet).toHaveBeenCalled());
    const params = mockGet.mock.calls[0][1]?.params;
    expect(params.sort).toBe('-listed_num');
  });

  it('leaves New Arrivals alone', async () => {
    render(<GoodsListPage mode='new' />);

    expect(await screen.findByText('New Arrivals')).toBeTruthy();
    await waitFor(() => expect(mockGet).toHaveBeenCalled());
    expect(mockGet.mock.calls[0][1]?.params.sort).toBe('-created_epoch');
  });
});
