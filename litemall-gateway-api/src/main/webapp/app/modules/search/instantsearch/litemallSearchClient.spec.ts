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
