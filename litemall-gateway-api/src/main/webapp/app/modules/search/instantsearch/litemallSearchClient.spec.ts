jest.mock('app/config/axiosinstance', () => ({ baseAxios: { get: jest.fn() } }));

import { baseAxios } from 'app/config/axiosinstance';
import store from 'app/config/store';
import { createSearchClient, PRIMARY_INDEX } from './litemallSearchClient';

/**
 * Wave-19 adapter wiring: the "Has coupon" toggle emits the Algolia facet
 * filter `coupon_flag:1`, which must reach `/srv/search` as the flat
 * `coupon_flag=1` param (the OCS-side filter field); hits must pass the
 * numeric `coupon_flag` source field through to the card; and the backend's
 * typed 502 outage must surface as `search.meta.unavailable` instead of being
 * collapsed into a generic empty result.
 */

const mockedGet = baseAxios.get as jest.Mock;

const okBody = (goodsList: any[] = [], extra: Record<string, unknown> = {}) => ({
  data: {
    errno: 0,
    data: {
      goodsList,
      total: goodsList.length,
      totalPages: 1,
      page: 1,
      limit: 12,
      filters: [],
      ...extra,
    },
  },
});

const search = (params: Record<string, unknown>) =>
  createSearchClient()
    .search([{ indexName: PRIMARY_INDEX, params } as any])
    .then((r: any) => r.results[0]);

const lastRequestParams = (): URLSearchParams => {
  const url: string = mockedGet.mock.calls[mockedGet.mock.calls.length - 1][0];
  return new URLSearchParams(url.split('?')[1] ?? '');
};

describe('litemallSearchClient coupon_flag wiring', () => {
  beforeEach(() => {
    mockedGet.mockReset();
  });

  it('maps the coupon_flag:1 facet filter onto the coupon_flag=1 request param', async () => {
    mockedGet.mockResolvedValue(okBody());
    await search({ query: 'lamp', facetFilters: [['coupon_flag:1']], hitsPerPage: 12, page: 0 });
    const qs = lastRequestParams();
    expect(qs.get('q')).toBe('lamp');
    expect(qs.get('coupon_flag')).toBe('1');
  });

  it('keeps coupon_flag alongside other facet filters (no clobbering)', async () => {
    mockedGet.mockResolvedValue(okBody());
    await search({
      query: '',
      facetFilters: [['coupon_flag:1'], ['brand:Acme'], ['category_ids:1005000']],
      numericFilters: ['price>=10', 'price<=50'],
      hitsPerPage: 12,
      page: 0,
    });
    const qs = lastRequestParams();
    expect(qs.get('coupon_flag')).toBe('1');
    expect(qs.get('brand')).toBe('Acme');
    expect(qs.get('category_ids')).toBe('1005000');
    expect(qs.get('price')).toBe('10,50');
  });

  it('passes the numeric coupon_flag hit source field through to the hits', async () => {
    mockedGet.mockResolvedValue(
      okBody([
        { id: 10008302, name: 'Couponed', coupon_flag: 1 },
        { id: 10008303, name: 'Plain', coupon_flag: 0 },
      ])
    );
    const result = await search({ query: '', hitsPerPage: 12, page: 0 });
    expect(result.hits).toHaveLength(2);
    expect(result.hits[0].coupon_flag).toBe(1);
    expect(result.hits[0].objectID).toBe('10008302');
    expect(result.hits[1].coupon_flag).toBe(0);
  });

  it('publishes meta.unavailable on the typed 502 outage errno', async () => {
    mockedGet.mockResolvedValue({ data: { errno: 502, errmsg: 'Search is temporarily unavailable' } });
    const result = await search({ query: 'lamp', hitsPerPage: 12, page: 0 });
    expect(result.nbHits).toBe(0);
    expect(store.getState().search.meta.unavailable).toBe(true);
  });

  it('publishes meta.unavailable when the gateway is unreachable', async () => {
    mockedGet.mockRejectedValue(new Error('network down'));
    const result = await search({ query: 'lamp', hitsPerPage: 12, page: 0 });
    expect(result.nbHits).toBe(0);
    expect(store.getState().search.meta.unavailable).toBe(true);
  });

  it('clears meta.unavailable again on the next successful response', async () => {
    mockedGet.mockResolvedValue({ data: { errno: 502, errmsg: 'Search is temporarily unavailable' } });
    await search({ query: 'lamp', hitsPerPage: 12, page: 0 });
    expect(store.getState().search.meta.unavailable).toBe(true);

    mockedGet.mockResolvedValue(okBody([{ id: 1, name: 'Back', coupon_flag: 1 }]));
    await search({ query: 'lamp', hitsPerPage: 12, page: 0 });
    expect(store.getState().search.meta.unavailable).toBe(false);
  });

  it('does NOT mark a plain non-outage errno as unavailable', async () => {
    mockedGet.mockResolvedValue(okBody()); // reset meta to a clean success first
    await search({ query: 'x', hitsPerPage: 12, page: 0 });
    mockedGet.mockResolvedValue({ data: { errno: 400, errmsg: 'bad params' } });
    const result = await search({ query: 'x', hitsPerPage: 12, page: 0 });
    expect(result.nbHits).toBe(0);
    expect(store.getState().search.meta.unavailable).toBe(false);
  });
});

/**
 * Wave-21: the "Group buy" toggle emits `groupon_flag:1` and rides the SAME
 * generic facet-filter → flat-param mapping and hit passthrough as
 * coupon_flag — these tests pin that contract for the new field.
 */
describe('litemallSearchClient groupon_flag wiring', () => {
  beforeEach(() => {
    mockedGet.mockReset();
  });

  it('maps the groupon_flag:1 facet filter onto the groupon_flag=1 request param', async () => {
    mockedGet.mockResolvedValue(okBody());
    await search({ query: 'lamp', facetFilters: [['groupon_flag:1']], hitsPerPage: 12, page: 0 });
    const qs = lastRequestParams();
    expect(qs.get('q')).toBe('lamp');
    expect(qs.get('groupon_flag')).toBe('1');
  });

  it('keeps groupon_flag alongside coupon_flag and other facet filters', async () => {
    mockedGet.mockResolvedValue(okBody());
    await search({
      query: '',
      facetFilters: [['groupon_flag:1'], ['coupon_flag:1'], ['brand:Acme']],
      hitsPerPage: 12,
      page: 0,
    });
    const qs = lastRequestParams();
    expect(qs.get('groupon_flag')).toBe('1');
    expect(qs.get('coupon_flag')).toBe('1');
    expect(qs.get('brand')).toBe('Acme');
  });

  it('passes the numeric groupon_flag hit source field through to the hits', async () => {
    mockedGet.mockResolvedValue(
      okBody([
        { id: 10008302, name: 'Groupable', groupon_flag: 1 },
        { id: 10008303, name: 'Plain', groupon_flag: 0 },
      ])
    );
    const result = await search({ query: '', hitsPerPage: 12, page: 0 });
    expect(result.hits).toHaveLength(2);
    expect(result.hits[0].groupon_flag).toBe(1);
    expect(result.hits[0].objectID).toBe('10008302');
    expect(result.hits[1].groupon_flag).toBe(0);
  });
});

/**
 * Wave-27 eu_flag wiring. The adapter is generic — it folds any
 * `field:value` facet filter into a flat param — so this is a guard, not a
 * new code path: the whole "In EU stock" filter rests on that generality, and
 * a future special case in buildQuery would break it silently.
 *
 * Verified against the live index while writing this: `?eu_flag=1` returns
 * 921 of 4,135 products and each hit carries `eu_flag: 1` and no country,
 * which is why the card copy names a stock reading rather than an origin.
 */
describe('litemallSearchClient eu_flag wiring', () => {
  beforeEach(() => {
    mockedGet.mockReset();
  });

  it('maps the eu_flag:1 facet filter onto the eu_flag=1 request param', async () => {
    mockedGet.mockResolvedValue(okBody());
    await search({ query: 'shears', facetFilters: [['eu_flag:1']], hitsPerPage: 12, page: 0 });
    const qs = lastRequestParams();
    expect(qs.get('eu_flag')).toBe('1');
    expect(qs.get('q')).toBe('shears');
  });

  it('combines with the offer toggles instead of replacing them', async () => {
    mockedGet.mockResolvedValue(okBody());
    await search({
      query: '',
      facetFilters: [['eu_flag:1'], ['coupon_flag:1'], ['category_ids:1036143']],
      hitsPerPage: 12,
      page: 0,
    });
    const qs = lastRequestParams();
    expect(qs.get('eu_flag')).toBe('1');
    expect(qs.get('coupon_flag')).toBe('1');
    expect(qs.get('category_ids')).toBe('1036143');
  });

  it('passes the numeric eu_flag hit field through to the card', async () => {
    mockedGet.mockResolvedValue(okBody([{ id: 10017029, name: 'Vegetable cutter', eu_flag: 1 }]));
    const res = await search({ query: '', hitsPerPage: 12, page: 0 });
    expect(res.hits[0].eu_flag).toBe(1);
  });

  it('leaves the field absent on products the index does not flag', async () => {
    // Positive-only passthrough: the backend omits the key rather than
    // emitting 0, and the card reads a missing key as "not known".
    mockedGet.mockResolvedValue(okBody([{ id: 10000900, name: 'Air purifier' }]));
    const res = await search({ query: '', hitsPerPage: 12, page: 0 });
    expect(res.hits[0].eu_flag).toBeUndefined();
  });
});
