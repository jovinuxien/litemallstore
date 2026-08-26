import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';

/**
 * Wave 26 — the drawer is one of the three places (header strip, "☰ All"
 * drawer, footer) that advertised Brands and Topics on a narrowed storefront
 * where neither has anything behind it. It reads the same availability probe as
 * the pages it links to, so this is the wiring test for all three.
 *
 * The network seam is the shared axios instance, mocked whole.
 */
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn(), post: jest.fn() },
}));

import { baseAxios } from 'app/config/axiosinstance';
import store from 'app/config/store';
import { __resetContentAvailability } from 'app/shared/util/contentAvailability';
import { __resetSeason } from 'app/shared/util/season';

import CategoryDrawer from './CategoryDrawer';

const mockGet = baseAxios.get as jest.Mock;

const envelope = (data: unknown) => Promise.resolve({ data: { errno: 0, data } });

const wire = (opts: { brandCount?: number; topicGoods?: unknown[]; fail?: boolean; season?: unknown } = {}) => {
  mockGet.mockImplementation((url: string) => {
    if (url.includes('/page/season')) {
      // Wave 27: no season running is the storefront's normal state.
      return opts.season ? envelope(opts.season) : Promise.resolve({ data: { errno: 642, errmsg: 'no active season page' } });
    }
    if (opts.fail) return Promise.reject(new Error('down'));
    if (url.includes('/brand/list')) {
      return envelope({ total: 1, list: [{ id: 1022000, name: 'Seed brand', kind: 0, displayEnabled: 1, goodsCount: opts.brandCount ?? 0 }] });
    }
    if (url.includes('/topic/list')) return envelope({ total: 1, list: [{ id: 264, title: 'Seasonal picks' }] });
    if (url.includes('/topic/detail')) return envelope({ topic: { id: 264 }, goods: opts.topicGoods ?? [] });
    return envelope({ total: 0, list: [] });
  });
};

const renderDrawer = () =>
  render(
    <Provider store={store}>
      <MemoryRouter>
        <CategoryDrawer show onHide={() => undefined} />
      </MemoryRouter>
    </Provider>
  );

// react-bootstrap's Offcanvas asks for a breakpoint; jsdom ships no matchMedia.
beforeAll(() => {
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

describe('CategoryDrawer content entries', () => {
  it('does not advertise Brands or Topics while both are empty', async () => {
    wire();
    renderDrawer();
    // Something the drawer always renders, so we are asserting on a settled DOM.
    await screen.findByText('Your orders');
    await waitFor(() => expect(mockGet).toHaveBeenCalled());
    await waitFor(() => expect(screen.queryByText('Brands')).toBeNull());
    expect(screen.queryByText('Topics & guides')).toBeNull();
  });

  it('lists Brands again once a brand has goods behind it', async () => {
    wire({ brandCount: 5 });
    renderDrawer();
    expect(await screen.findByText('Brands')).toBeTruthy();
  });

  it('lists Topics again once a topic points at products', async () => {
    wire({ topicGoods: [{ id: 10001 }] });
    renderDrawer();
    expect(await screen.findByText('Topics & guides')).toBeTruthy();
  });

  it('keeps both entries when the probes fail — an outage must not remove navigation', async () => {
    wire({ fail: true });
    renderDrawer();
    expect(await screen.findByText('Brands')).toBeTruthy();
    expect(await screen.findByText('Topics & guides')).toBeTruthy();
  });

  it('never hides the sections that always have somewhere to go', async () => {
    wire();
    renderDrawer();
    // Was "Today’s Deals", which is the header strip's label for /deals — this
    // link goes to /hot, a different page. The drawer now says what /hot is.
    expect(await screen.findByText('Best sellers')).toBeTruthy();
    expect(screen.getByText('New Arrivals')).toBeTruthy();
    expect(screen.getByText('Customer service')).toBeTruthy();
  });

  it('does not claim the name the header gives /deals', async () => {
    wire();
    renderDrawer();
    await screen.findByText('Best sellers');
    expect(screen.queryByText(/Today.s Deals/i)).toBeNull();
    expect(document.querySelector('a[href="/hot"]')?.textContent).toBe('Best sellers');
  });
});

/**
 * Wave 27 — the season entry that replaced the hardcoded "Summer Deals" link.
 * The drawer, the header strip and the home rail all read the same resolver,
 * so this is the wiring test for the nav rule: a season is advertised by NAME
 * while one is running, and not at all when none is.
 */
describe('CategoryDrawer season entry', () => {
  const season = (over: Record<string, unknown> = {}) => ({
    id: 31,
    name: 'Autumn Deals',
    category: 'season',
    components: [{ type: 'goods-list', config: { mode: 'byIds', goodsIds: [10034827] } }],
    ...over,
  });

  it('links the running season under the name the admin gave it', async () => {
    wire({ season: season() });
    renderDrawer();
    const link = await screen.findByText('Autumn Deals');
    expect(link.getAttribute('href')).toBe('/page/31');
  });

  it('shows no season entry when none is running, and nothing of the old hardcoded link survives', async () => {
    wire();
    renderDrawer();
    await screen.findByText('Your orders');
    await waitFor(() => expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('/page/season')));
    expect(screen.queryByText('Autumn Deals')).toBeNull();
    expect(screen.queryByText('Summer Deals')).toBeNull();
  });

  it('shows no season entry when the season page has no name to show', async () => {
    wire({ season: season({ name: '' }) });
    renderDrawer();
    await screen.findByText('Your orders');
    await waitFor(() => expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('/page/season')));
    expect(document.querySelector('a[href="/page/31"]')).toBeNull();
  });
});
