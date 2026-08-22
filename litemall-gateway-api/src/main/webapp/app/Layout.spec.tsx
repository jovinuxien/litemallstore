import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';

/**
 * Wave 27 — the header strip, which is where "Summer Deals" was hardcoded and
 * where a shopper is most likely to meet the season. The strip advertises the
 * running season BY NAME, or says nothing at all: a nav entry pointing at a
 * collection that is not running is exactly what this wave removes.
 */
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn(), post: jest.fn() },
}));

import { baseAxios } from 'app/config/axiosinstance';
import store from 'app/config/store';
import { __resetContentAvailability } from 'app/shared/util/contentAvailability';
import { __resetSeason } from 'app/shared/util/season';

import Layout from './Layout';

const mockGet = baseAxios.get as jest.Mock;

const envelope = (data: unknown) => Promise.resolve({ data: { errno: 0, data } });

const SEASON = {
  id: 31,
  name: 'Autumn Deals',
  category: 'season',
  components: [{ type: 'goods-list', config: { mode: 'byIds', goodsIds: [10034827] } }],
};

const wire = (opts: { season?: unknown; seasonFails?: boolean } = {}) => {
  mockGet.mockImplementation((url: string) => {
    if (url.includes('/page/season')) {
      if (opts.seasonFails) return Promise.reject(new Error('down'));
      return opts.season ? envelope(opts.season) : Promise.resolve({ data: { errno: 642, errmsg: 'no active season page' } });
    }
    return envelope({ total: 0, list: [] });
  });
};

const renderLayout = () =>
  render(
    <Provider store={store}>
      <MemoryRouter>
        <Layout />
      </MemoryRouter>
    </Provider>
  );

beforeAll(() => {
  // Layout pulls in the site-config store (social links, pixel ids), which
  // reads global fetch; jsdom ships none. An empty config is the honest
  // default it already degrades to.
  (global as unknown as { fetch: unknown }).fetch = () => Promise.resolve({ json: () => Promise.resolve({ data: {} }) });
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      addListener: () => undefined,
      removeListener: () => undefined,
      onchange: null,
      dispatchEvent: () => false,
    }),
  });
});

beforeEach(() => {
  mockGet.mockReset();
  __resetContentAvailability();
  __resetSeason();
  sessionStorage.clear();
});

it('links the running season by name', async () => {
  wire({ season: SEASON });
  renderLayout();
  const link = await screen.findByText('Autumn Deals');
  expect(link.getAttribute('href')).toBe('/page/31');
});

it('shows no season link when none is running', async () => {
  wire();
  renderLayout();
  await screen.findByText('All Products');
  await waitFor(() => expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('/page/season')));
  expect(screen.queryByText('Autumn Deals')).toBeNull();
});

it('has no trace of the hardcoded Summer Deals link it replaced', async () => {
  wire({ season: SEASON });
  renderLayout();
  await screen.findByText('Autumn Deals');
  expect(screen.queryByText('Summer Deals')).toBeNull();
  expect(document.querySelector('a[href="/summer"]')).toBeNull();
});

it('shows no season link when the season cannot be read — there is no link target to guess', async () => {
  wire({ seasonFails: true });
  renderLayout();
  await screen.findByText('All Products');
  await waitFor(() => expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('/page/season')));
  expect(document.querySelector('.lm-header__strip-link[href^="/page/"]')).toBeNull();
});

it('keeps the permanent strip entries either way', async () => {
  wire();
  renderLayout();
  expect(await screen.findByText('Today’s Deals')).toBeTruthy();
  expect(screen.getByText('New Arrivals')).toBeTruthy();
  expect(screen.getByText('All Products')).toBeTruthy();
});
