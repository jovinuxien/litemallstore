import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';

/**
 * Wave 27 — the home strip. The spec's degrade rule is the test: with no
 * season running (or no products behind the one that is), the home page shows
 * NOTHING here — not a heading over an empty grid, and not a "See more" link
 * into a collection that isn't there.
 */
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn(), post: jest.fn() },
}));

import { baseAxios } from 'app/config/axiosinstance';
import store from 'app/config/store';
import { __resetSeason } from 'app/shared/util/season';

import SeasonRail from './SeasonRail';

const mockGet = baseAxios.get as jest.Mock;
const mockPost = baseAxios.post as jest.Mock;

const envelope = (data: unknown) => Promise.resolve({ data: { errno: 0, data } });

const seasonPage = (over: Record<string, unknown> = {}) => ({
  id: 31,
  name: 'Autumn Deals',
  category: 'season',
  components: [{ type: 'goods-list', config: { mode: 'byIds', goodsIds: [10034827, 10010060] } }],
  ...over,
});

const wire = (opts: { season?: unknown; errno?: number; goods?: Record<string, unknown> } = {}) => {
  mockGet.mockImplementation((url: string) => {
    if (url.includes('/page/season')) {
      return opts.errno ? Promise.resolve({ data: { errno: opts.errno, errmsg: 'no active season page' } }) : envelope(opts.season);
    }
    return envelope({ list: [] });
  });
  mockPost.mockImplementation(() =>
    Promise.resolve({
      data: opts.goods ?? {
        '10034827': { id: 10034827, name: '2-Pack Lighted Fall Garland', retailPrice: 67.1 },
        '10010060': { id: 10010060, name: 'Rust Throw Blanket', retailPrice: 58.43 },
      },
    })
  );
};

const renderRail = () =>
  render(
    <Provider store={store}>
      <MemoryRouter>
        <SeasonRail />
      </MemoryRouter>
    </Provider>
  );

beforeEach(() => {
  __resetSeason();
  mockGet.mockReset();
  mockPost.mockReset();
});

it('renders the season under its own name, linking to its page', async () => {
  wire({ season: seasonPage() });
  renderRail();
  expect(await screen.findByText('Autumn Deals')).toBeTruthy();
  expect(screen.getByText('See more ›').getAttribute('href')).toBe('/page/31');
  expect(await screen.findByText('2-Pack Lighted Fall Garland')).toBeTruthy();
});

it('shows the products the admin curated, in the order they curated them', async () => {
  wire({ season: seasonPage() });
  const { container } = renderRail();
  await screen.findByText('Autumn Deals');
  await waitFor(() => expect(container.querySelectorAll('.lm-rail a').length).toBeGreaterThan(0));
  const names = Array.from(container.querySelectorAll('.lm-rail')).map(r => r.textContent ?? '');
  expect(names.join(' ').indexOf('Fall Garland')).toBeLessThan(names.join(' ').indexOf('Rust Throw Blanket'));
});

it('renders nothing at all when no season is running (errno 642)', async () => {
  wire({ errno: 642 });
  const { container } = renderRail();
  await waitFor(() => expect(mockGet).toHaveBeenCalled());
  expect(container.querySelector('.lm-section')).toBeNull();
});

it('renders nothing when the season page carries no product rail', async () => {
  wire({ season: seasonPage({ components: [{ type: 'rich-text', config: { html: '<p>soon</p>' } }] }) });
  const { container } = renderRail();
  await waitFor(() => expect(mockGet).toHaveBeenCalled());
  expect(container.querySelector('.lm-section')).toBeNull();
});

it('renders nothing when the rail resolves empty — a heading over no products advertises nothing', async () => {
  wire({ season: seasonPage(), goods: {} });
  const { container } = renderRail();
  await waitFor(() => expect(mockPost).toHaveBeenCalled());
  await waitFor(() => expect(container.querySelector('.lm-section')).toBeNull());
});

it('renders nothing when the season has no name to show', async () => {
  wire({ season: seasonPage({ name: '  ' }) });
  const { container } = renderRail();
  await waitFor(() => expect(mockGet).toHaveBeenCalled());
  expect(container.querySelector('.lm-section')).toBeNull();
});
