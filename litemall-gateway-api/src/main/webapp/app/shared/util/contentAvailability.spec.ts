/**
 * Wave 26 — nav honesty probes.
 *
 * The fixtures are the shapes trovemo.com actually served on 2026-08-18, after
 * the catalogue was narrowed to the anchor cluster: all 49 seed brands report
 * `goodsCount 0`, every seeded topic detail carries an empty goods list, the
 * article CMS is empty and no group-buy campaign is running. Those are the
 * payloads that used to produce four nav entries pointing at nothing.
 *
 * The other half of these tests is the opposite case — content EXISTS, or the
 * probe FAILED — because a rule that only ever hides would quietly amputate the
 * store's navigation the first time an endpoint hiccups.
 */
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn(), post: jest.fn() },
}));

import { baseAxios } from 'app/config/axiosinstance';
import {
  __resetContentAvailability,
  loadBrandsWithGoods,
  loadTopicsWithGoods,
  surfaceAvailable,
  TOPIC_PROBE_LIMIT,
} from './contentAvailability';

const mockGet = baseAxios.get as jest.Mock;

/** Live shape: 49 rows, kind 0, display-enabled, every one of them goodsCount 0. */
const seedBrands = (count = 3, goodsCount = 0) =>
  Array.from({ length: count }, (_, i) => ({
    id: 1022000 + i,
    name: `Seed brand ${i}`,
    picUrl: 'http://yanxuan.nosdn.127.net/seed.png',
    kind: 0,
    displayEnabled: 1,
    goodsCount,
  }));

const seedTopics = (count = 20) =>
  Array.from({ length: count }, (_, i) => ({ id: 260 + i, title: `Seed topic ${i}`, picUrl: 'http://yanxuan.nosdn.127.net/t.jpg' }));

interface Wiring {
  brands?: unknown[];
  topics?: unknown[];
  /** topic id -> goods it points at */
  topicGoods?: Record<number, unknown[]>;
  articleTotal?: number;
  grouponTotal?: number;
  fail?: RegExp;
}

const wire = (opts: Wiring = {}) => {
  mockGet.mockImplementation((url: string) => {
    if (opts.fail?.test(url)) return Promise.reject(new Error('down'));
    if (url.includes('/brand/list')) {
      const list = opts.brands ?? seedBrands();
      return Promise.resolve({ data: { errno: 0, data: { list, total: list.length } } });
    }
    if (url.includes('/topic/list')) {
      const list = opts.topics ?? seedTopics();
      return Promise.resolve({ data: { errno: 0, data: { list, total: list.length } } });
    }
    if (url.includes('/topic/detail')) {
      const id = Number(new URL(url, 'http://x').searchParams.get('id'));
      return Promise.resolve({ data: { errno: 0, data: { topic: { id }, goods: opts.topicGoods?.[id] ?? [] } } });
    }
    if (url.includes('/article/list')) {
      return Promise.resolve({ data: { errno: 0, data: { list: [], total: opts.articleTotal ?? 0 } } });
    }
    if (url.includes('/groupon/list')) {
      return Promise.resolve({ data: { errno: 0, data: { list: [], total: opts.grouponTotal ?? 0 } } });
    }
    if (url.includes('/goods/list')) {
      return Promise.resolve({ data: { errno: 0, data: { list: [], total: 0 } } });
    }
    return Promise.reject(new Error(`unexpected GET ${url}`));
  });
};

beforeEach(() => {
  mockGet.mockReset();
  __resetContentAvailability();
  sessionStorage.clear();
});

describe('surface availability — the narrowed store as it is served today', () => {
  it('hides Brands: every seed brand has zero goods behind it', async () => {
    wire();
    await expect(surfaceAvailable('brands')).resolves.toBe(false);
  });

  it('hides Topics: every seeded topic points at no products', async () => {
    wire();
    await expect(surfaceAvailable('topics')).resolves.toBe(false);
  });

  it('hides Articles: the CMS is empty', async () => {
    wire();
    await expect(surfaceAvailable('articles')).resolves.toBe(false);
  });

  it('hides Group buys: no campaign is running', async () => {
    wire();
    await expect(surfaceAvailable('groupons')).resolves.toBe(false);
  });
});

describe('surface availability — content brings the entry back', () => {
  it('shows Brands as soon as one brand has goods', async () => {
    wire({ brands: [...seedBrands(2, 0), ...seedBrands(1, 7)] });
    await expect(surfaceAvailable('brands')).resolves.toBe(true);
  });

  it('shows Topics as soon as one probed topic points at a product', async () => {
    wire({ topicGoods: { 262: [{ id: 10001 }] } });
    await expect(surfaceAvailable('topics')).resolves.toBe(true);
  });

  it('shows Articles and Group buys when they have rows', async () => {
    wire({ articleTotal: 4, grouponTotal: 2 });
    await expect(surfaceAvailable('articles')).resolves.toBe(true);
    await expect(surfaceAvailable('groupons')).resolves.toBe(true);
  });
});

describe('surface availability — failure must not amputate the nav', () => {
  it('a failed probe resolves available (fail-open), on every surface', async () => {
    wire({ fail: /.*/ });
    await expect(surfaceAvailable('brands')).resolves.toBe(true);
    await expect(surfaceAvailable('topics')).resolves.toBe(true);
    await expect(surfaceAvailable('articles')).resolves.toBe(true);
    await expect(surfaceAvailable('groupons')).resolves.toBe(true);
  });

  it('a topic whose detail cannot be read is kept, not silently dropped', async () => {
    wire({ topics: seedTopics(2), fail: /topic\/detail/ });
    await expect(surfaceAvailable('topics')).resolves.toBe(true);
  });

  it('does not cache a failure as "empty" — the next probe re-reads', async () => {
    wire({ fail: /brand\/list/ });
    await expect(surfaceAvailable('brands')).resolves.toBe(true);
    __resetContentAvailability();
    wire();
    await expect(surfaceAvailable('brands')).resolves.toBe(false);
  });
});

describe('probe cost', () => {
  it('probes each surface once per session, however many callers ask', async () => {
    wire();
    await Promise.all([surfaceAvailable('articles'), surfaceAvailable('articles'), surfaceAvailable('articles')]);
    await surfaceAvailable('articles');
    expect(mockGet.mock.calls.filter(([url]) => String(url).includes('/article/list'))).toHaveLength(1);
  });

  it('reuses a resolved probe across a full page load via sessionStorage', async () => {
    wire();
    await surfaceAvailable('articles');
    __resetSessionOnly();
    await expect(surfaceAvailable('articles')).resolves.toBe(false);
    expect(mockGet.mock.calls.filter(([url]) => String(url).includes('/article/list'))).toHaveLength(1);
  });

  it('opens at most TOPIC_PROBE_LIMIT topics to decide the nav entry', async () => {
    wire({ topics: seedTopics(20) });
    await surfaceAvailable('topics');
    const details = mockGet.mock.calls.filter(([url]) => String(url).includes('/topic/detail'));
    expect(details).toHaveLength(TOPIC_PROBE_LIMIT);
  });

  it('trusts the backend goodsCount instead of probing every brand', async () => {
    wire({ brands: seedBrands(49, 0) });
    await loadBrandsWithGoods();
    expect(mockGet.mock.calls.filter(([url]) => String(url).includes('/goods/list'))).toHaveLength(0);
  });

  it('falls back to a per-brand count when the backend sends none (pre-Wave-25 payload)', async () => {
    const noCount = seedBrands(2, 0).map(({ goodsCount, ...rest }) => rest);
    wire({ brands: noCount });
    await loadBrandsWithGoods();
    expect(mockGet.mock.calls.filter(([url]) => String(url).includes('/goods/list'))).toHaveLength(2);
  });
});

describe('page listings agree with the nav', () => {
  it('drops the empty seed topics the /topics page used to render as dead tiles', async () => {
    wire({ topics: seedTopics(20), topicGoods: { 265: [{ id: 1 }], 271: [{ id: 2 }] } });
    const topics = await loadTopicsWithGoods();
    expect(topics.map(t => t.id)).toEqual([265, 271]);
  });

  it('never lists a brand row the Wave-25 curation gate hides', async () => {
    wire({
      brands: [
        { id: 1046002, name: 'XIN BO EDUCATIONAL CONSULTATION PTE. LTD.', kind: 1, displayEnabled: 0, goodsCount: 12 },
        { id: 1046003, name: 'Trovemo Home', kind: 1, displayEnabled: 1, goodsCount: 3 },
      ],
    });
    const brands = await loadBrandsWithGoods();
    expect(brands.map(b => b.name)).toEqual(['Trovemo Home']);
  });
});

/** Clears the in-memory memo only, leaving sessionStorage — i.e. a page reload. */
function __resetSessionOnly(): void {
  const cached = sessionStorage.getItem('lm_content_avail');
  __resetContentAvailability();
  if (cached) sessionStorage.setItem('lm_content_avail', cached);
}
