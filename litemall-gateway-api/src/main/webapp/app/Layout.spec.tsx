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
import { SUPPORT_HOURS } from 'app/modules/static/faqData';
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

/**
 * The four customer promises in the footer strip. They used to be inert text
 * making claims a shopper could not check — and two of those claims were wrong.
 * Each is now a link to the page that governs it.
 */
describe('footer promise strip', () => {
  beforeEach(() => {
    mockGet.mockReset();
    __resetContentAvailability();
    __resetSeason();
    wire();
  });

  it('links every promise to the page that governs it', async () => {
    renderLayout();
    await waitFor(() => expect(document.querySelector('.lm-promise')).toBeTruthy());

    const targets = Array.from(document.querySelectorAll('a.lm-promise')).map(a => a.getAttribute('href'));
    expect(targets).toEqual(['/delivery', '/payments', '/returns', '/help']);
  });

  it('states support hours from the shared constant, not its own copy', async () => {
    renderLayout();
    await waitFor(() => expect(document.querySelector('.lm-promise')).toBeTruthy());

    const help = document.querySelector('a.lm-promise[href="/help"]');
    expect(help?.textContent).toContain(SUPPORT_HOURS);
    // The claim that disagreed with /service for months.
    expect(help?.textContent).not.toContain('every day');
  });

  it('makes no promise the store cannot keep', async () => {
    renderLayout();
    await waitFor(() => expect(document.querySelector('.lm-promise')).toBeTruthy());

    const strip = Array.from(document.querySelectorAll('a.lm-promise')).map(a => a.textContent).join(' ');
    expect(strip).not.toMatch(/nationwide|hassle-free|exchanges/i);
  });
});
