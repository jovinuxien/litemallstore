import React from 'react';
import { render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import RecentlyViewed from './RecentlyViewed';

/**
 * Recently-viewed row: signed-in only (sessionStorage customerToken), sourced
 * from /srv/footprint/list, current product excluded, fail-silent throughout.
 */
const footprintList = jest.fn();
jest.mock('app/shared/api', () => ({
  ...jest.requireActual('app/shared/api'),
  userApi: { footprintList: (...args: unknown[]) => footprintList(...args) },
}));

const renderRow = (current: number) =>
  render(
    <MemoryRouter>
      <RecentlyViewed currentGoodsId={current} />
    </MemoryRouter>
  );

describe('RecentlyViewed', () => {
  beforeEach(() => {
    footprintList.mockReset();
    sessionStorage.clear();
  });

  it('does not even fetch for anonymous visitors', () => {
    const { container } = renderRow(1);
    expect(footprintList).not.toHaveBeenCalled();
    expect(container.firstChild).toBeNull();
  });

  it('renders history cards excluding the current product, slugged links', async () => {
    sessionStorage.setItem('customerToken', 't');
    footprintList.mockResolvedValue({
      list: [
        { id: 1, goodsId: 10008308, name: 'Sealer Mini', picUrl: '/a.jpg', retailPrice: 43.49 },
        { id: 2, goodsId: 10008302, name: 'Cat Hoodie', picUrl: '/b.jpg', retailPrice: 14.81 },
      ],
    });
    const { container, getByText } = renderRow(10008308);
    await waitFor(() => expect(container.querySelector('.lm-pdp__recent')).not.toBeNull());
    const cards = container.querySelectorAll('.lm-pdp__recentcard');
    expect(cards.length).toBe(1); // current product excluded
    expect(cards[0].getAttribute('href')).toBe('/product/10008302-cat-hoodie');
    expect(getByText(/14\.81/)).not.toBeNull();
  });

  it('renders nothing when the fetch fails or history is empty', async () => {
    sessionStorage.setItem('customerToken', 't');
    footprintList.mockRejectedValueOnce(new Error('down'));
    const a = renderRow(1);
    await waitFor(() => expect(footprintList).toHaveBeenCalled());
    expect(a.container.querySelector('.lm-pdp__recent')).toBeNull();

    footprintList.mockResolvedValueOnce({ list: [] });
    const b = renderRow(1);
    await waitFor(() => expect(footprintList).toHaveBeenCalledTimes(2));
    expect(b.container.querySelector('.lm-pdp__recent')).toBeNull();
  });
});
