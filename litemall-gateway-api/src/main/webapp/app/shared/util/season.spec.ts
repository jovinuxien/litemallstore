/**
 * Wave 27 — the season resolver's degrade rules, which are the whole point of
 * the wave: with no season running the storefront must say NOTHING, and it
 * must not confuse "no season is running" with "we could not ask".
 *
 * The network seam is the shared axios instance, mocked whole, so the real
 * envelope handling (`unwrap` → `ApiError`) runs — errno 642 detection is the
 * behaviour under test, not something worth stubbing out.
 */
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn(), post: jest.fn() },
}));

import { baseAxios } from 'app/config/axiosinstance';
import { IPageView } from 'app/shared/api';
import { __resetSeason, ERRNO_NO_ACTIVE_SEASON, firstGoodsList, loadSeason, seasonLabel, seasonPath } from './season';

const mockGet = baseAxios.get as jest.Mock;

const PAGE: IPageView = {
  id: 31,
  name: 'Autumn Deals',
  category: 'season',
  components: [
    { type: 'rich-text', config: { html: '<p>Cosy season</p>' } },
    { type: 'goods-list', config: { mode: 'byIds', goodsIds: [10034827, 10010060] } },
    { type: 'goods-list', config: { mode: 'deals' } },
  ],
};

const envelope = (data: unknown) => Promise.resolve({ data: { errno: 0, data } });
const errno = (n: number) => Promise.resolve({ data: { errno: n, errmsg: 'no active season page' } });

beforeEach(() => {
  __resetSeason();
  mockGet.mockReset();
});

describe('loadSeason', () => {
  it('returns the active season page', async () => {
    mockGet.mockReturnValue(envelope(PAGE));
    await expect(loadSeason()).resolves.toEqual(PAGE);
    expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('/page/season'));
  });

  it('resolves null on errno 642 — no season running is a normal state, not an error', async () => {
    mockGet.mockReturnValue(errno(ERRNO_NO_ACTIVE_SEASON));
    await expect(loadSeason()).resolves.toBeNull();
  });

  it('resolves null when the fetch fails: there is no link target to fail open to', async () => {
    mockGet.mockReturnValue(Promise.reject(new Error('down')));
    await expect(loadSeason()).resolves.toBeNull();
  });

  it('rejects a payload with no components rather than rendering a headless season', async () => {
    mockGet.mockReturnValue(envelope({ id: 4, name: 'Broken' }));
    await expect(loadSeason()).resolves.toBeNull();
  });

  it('asks once per page load however many surfaces read it', async () => {
    mockGet.mockReturnValue(envelope(PAGE));
    const [a, b, c] = await Promise.all([loadSeason(), loadSeason(), loadSeason()]);
    expect(mockGet).toHaveBeenCalledTimes(1);
    expect(a).toBe(b);
    expect(b).toBe(c);
  });

  it('caches "no season" (642 is an answer) but re-probes after a failure', async () => {
    mockGet.mockReturnValue(errno(ERRNO_NO_ACTIVE_SEASON));
    await loadSeason();
    await loadSeason();
    expect(mockGet).toHaveBeenCalledTimes(1);

    __resetSeason();
    mockGet.mockReset();
    mockGet.mockReturnValue(Promise.reject(new Error('down')));
    await loadSeason();
    // A season activated seconds later must not be hidden by a cached failure.
    mockGet.mockReturnValue(envelope(PAGE));
    await expect(loadSeason()).resolves.toEqual(PAGE);
    expect(mockGet).toHaveBeenCalledTimes(2);
  });
});

describe('seasonLabel', () => {
  it('is the admin-given page name — never a hardcoded season', () => {
    expect(seasonLabel(PAGE)).toBe('Autumn Deals');
  });

  it('is null for a blank name: an unnamed nav link is as bad as a dead one', () => {
    expect(seasonLabel({ ...PAGE, name: '   ' })).toBeNull();
    expect(seasonLabel({ ...PAGE, name: undefined })).toBeNull();
    expect(seasonLabel(null)).toBeNull();
  });
});

describe('seasonPath', () => {
  it('points at the season page, which /page/:id already renders', () => {
    expect(seasonPath(PAGE)).toBe('/page/31');
  });
});

describe('firstGoodsList', () => {
  it('takes the FIRST goods-list component, whatever precedes it', () => {
    expect(firstGoodsList(PAGE)?.config).toEqual({ mode: 'byIds', goodsIds: [10034827, 10010060] });
  });

  it('is null when the season carries no product rail at all', () => {
    expect(firstGoodsList({ ...PAGE, components: [{ type: 'rich-text', config: {} }] })).toBeNull();
    expect(firstGoodsList(null)).toBeNull();
  });
});
