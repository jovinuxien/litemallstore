import React from 'react';
import { act, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';

/**
 * The season chip on the results grid.
 *
 * goods-management scores every season nightly and ships the memberships on
 * each hit (`seasons: ["autumn","winter"]`). The card names ONE of them: the
 * season the store is RUNNING, read from the active season page's rail. So
 * the rules under test are about absence — no running season, a rail with no
 * key, a product outside the running season — and about who asks: the card
 * never does, it listens to what the header strip loaded.
 */
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn(), post: jest.fn() },
}));
jest.mock('app/shared/config/siteConfig', () => ({ loadSiteConfig: jest.fn().mockResolvedValue({ i18nLanguages: ['en'] }) }));

import { baseAxios } from 'app/config/axiosinstance';
import store from 'app/config/store';
import { __resetLocale, applyEnabledLanguages, setLocale } from 'app/i18n/locale';
import { IGood } from 'app/shared/model/product/product.model';
import { __resetSeason, loadSeason } from 'app/shared/util/season';

import ProductCard from './ProductCard';
import { productSeasons, seasonChip } from './seasonBadge';

const mockGet = baseAxios.get as jest.Mock;

const envelope = (data: unknown) => Promise.resolve({ data: { errno: 0, data } });

const seasonPage = (railConfig: Record<string, unknown>, name = 'Autumns Deal') => ({
  id: 5,
  name,
  category: 'season',
  components: [
    { type: 'rich-text', config: { html: '<p>Autumn at Trovemo.</p>' } },
    { type: 'goods-list', config: railConfig },
  ],
});

/** The rail exactly as goods-management serves a resolved season rule (live shape, 2026-10-10). */
const AUTUMN_RAIL = { mode: 'byIds', seasonKey: 'autumn', goodsIds: [10052252, 10051446], resolvedFrom: 'season' };

/** Loads the season the way the header strip does, so the snapshot the card listens to is filled. */
const runningSeason = async (railConfig: Record<string, unknown> | null, name?: string): Promise<void> => {
  mockGet.mockImplementation((url: string) =>
    url.includes('/page/season')
      ? railConfig
        ? envelope(seasonPage(railConfig, name))
        : Promise.resolve({ data: { errno: 642, errmsg: 'no active season page' } })
      : envelope({ list: [] })
  );
  await loadSeason();
};

const card = (product: Partial<IGood> & Record<string, unknown>) =>
  render(
    <Provider store={store}>
      <MemoryRouter>
        <ProductCard product={product as IGood} />
      </MemoryRouter>
    </Provider>
  );

const BASE = { id: 10052252, name: 'Fleece blanket', picUrl: '/x.jpg', retailPrice: 19.9, counterPrice: 19.9 };

beforeEach(() => {
  __resetSeason();
  mockGet.mockReset();
});

describe('season chip on a product card', () => {
  it('names the running season on a product that belongs to it', async () => {
    await runningSeason(AUTUMN_RAIL);
    card({ ...BASE, seasons: ['autumn', 'winter'] });
    expect(screen.getByText('Autumn')).toBeTruthy();
    // Both memberships are facts, but only the running one is shown.
    expect(screen.queryByText('Winter')).toBeNull();
  });

  it('says nothing before anyone has loaded the season — the card never asks', () => {
    card({ ...BASE, seasons: ['autumn'] });
    expect(screen.queryByText('Autumn')).toBeNull();
    expect(mockGet).not.toHaveBeenCalled();
  });

  it('says nothing while no season is running (errno 642)', async () => {
    await runningSeason(null);
    card({ ...BASE, seasons: ['autumn'] });
    expect(screen.queryByText('Autumn')).toBeNull();
  });

  it('says nothing for a hand-curated rail that names no season key', async () => {
    await runningSeason({ mode: 'byIds', goodsIds: [10052252] });
    card({ ...BASE, seasons: ['autumn'] });
    // The page is called "Autumns Deal", but a name is not a key: the product
    // cannot be related to a collection whose season nobody declared.
    expect(screen.queryByText('Autumn')).toBeNull();
  });

  it('never puts an off-season chip on a card: a summer-only product in autumn shows none', async () => {
    await runningSeason(AUTUMN_RAIL);
    card({ ...BASE, seasons: ['summer'] });
    expect(screen.queryByText('Summer')).toBeNull();
    expect(screen.queryByText('Autumn')).toBeNull();
  });

  it('says nothing when the hit carries no seasons at all', async () => {
    await runningSeason(AUTUMN_RAIL);
    card(BASE);
    expect(document.querySelector('.lm-card__season')).toBeNull();
  });

  it('tolerates a bare single value and loose casing, as the backend normalises them', async () => {
    await runningSeason(AUTUMN_RAIL);
    card({ ...BASE, seasons: ' AUTUMN ' });
    expect(screen.getByText('Autumn')).toBeTruthy();
  });

  it('explains itself as membership of the collection, by the page name the admin gave it', async () => {
    await runningSeason(AUTUMN_RAIL, 'Autumns Deal');
    card({ ...BASE, seasons: ['autumn'] });
    expect(screen.getByTitle('In our Autumns Deal collection.')).toBeTruthy();
  });

  it('is context, not an offer: coexists with a live deal overlay and the EU stock chip', async () => {
    await runningSeason(AUTUMN_RAIL);
    card({ ...BASE, seasons: ['autumn'], hot: true, eu_flag: 1, coupon_flag: 1 });
    expect(screen.getByText('Limited time deal')).toBeTruthy();
    expect(screen.getByText('EU stock')).toBeTruthy();
    expect(screen.getByText('Coupon')).toBeTruthy();
    expect(screen.getByText('Autumn')).toBeTruthy();
    // Never on the image overlay, where ONE badge at most is the rule.
    expect(document.querySelector('.lm-card__media .lm-card__season')).toBeNull();
  });

  it('appears on an already-mounted card once the season lands (the header loads it after the grid)', async () => {
    card({ ...BASE, seasons: ['autumn'] });
    expect(screen.queryByText('Autumn')).toBeNull();
    await act(async () => {
      await runningSeason(AUTUMN_RAIL);
    });
    expect(screen.getByText('Autumn')).toBeTruthy();
  });

  it('renders a season goods-management adds later capitalised, never as a raw token', async () => {
    await runningSeason({ ...AUTUMN_RAIL, seasonKey: 'christmas' });
    card({ ...BASE, seasons: ['christmas'] });
    expect(screen.getByText('Christmas')).toBeTruthy();
  });

  it('follows the locale', async () => {
    await __resetLocale();
    await applyEnabledLanguages(['sv', 'da']);
    try {
      await runningSeason(AUTUMN_RAIL);
      await setLocale('sv');
      card({ ...BASE, seasons: ['autumn'] });
      expect(screen.getByText('Höst')).toBeTruthy();
      expect(screen.getByTitle('Ingår i vår samling Autumns Deal.')).toBeTruthy();
    } finally {
      // The card is still mounted here (cleanup runs afterEach): switching the
      // language back re-renders it, so the reset has to run inside act().
      await act(async () => {
        await __resetLocale();
      });
    }
  });
});

describe('seasonBadge helpers', () => {
  it('productSeasons normalises arrays, bare strings and junk', () => {
    expect(productSeasons(['autumn', ' Winter', 7, null, ''])).toEqual(['autumn', 'winter']);
    expect(productSeasons('spring')).toEqual(['spring']);
    expect(productSeasons(undefined)).toEqual([]);
    expect(productSeasons({ autumn: true })).toEqual([]);
  });

  it('seasonChip needs a running season WITH a key and a product inside it', () => {
    const page = seasonPage(AUTUMN_RAIL) as never;
    expect(seasonChip({ seasons: ['autumn'] }, page)).toBe('autumn');
    expect(seasonChip({ seasons: ['winter'] }, page)).toBeNull();
    expect(seasonChip({ seasons: ['autumn'] }, null)).toBeNull();
    expect(seasonChip({ seasons: ['autumn'] }, seasonPage({ mode: 'byIds', goodsIds: [1] }) as never)).toBeNull();
    expect(seasonChip(null, page)).toBeNull();
  });
});
