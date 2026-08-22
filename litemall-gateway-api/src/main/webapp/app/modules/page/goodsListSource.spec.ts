/**
 * Wave 27 — the one resolver behind both the season's page and the home rail.
 *
 * The behaviour that matters commercially is byIds ORDER: an admin curating a
 * season chooses the sequence, and the rail shows the leading picks, so a
 * reordering bug here silently re-merchandises the storefront.
 */
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn(), post: jest.fn() },
}));

import { baseAxios } from 'app/config/axiosinstance';
import { loadGoodsListGoods } from './goodsListSource';

const mockGet = baseAxios.get as jest.Mock;
const mockPost = baseAxios.post as jest.Mock;

const good = (id: number) => ({ id, name: `Product ${id}` });
const envelope = (data: unknown) => Promise.resolve({ data: { errno: 0, data } });

beforeEach(() => {
  mockGet.mockReset();
  mockPost.mockReset();
});

describe('byIds', () => {
  it('returns the admin-configured order, not the order the map came back in', async () => {
    mockPost.mockReturnValue(Promise.resolve({ data: { '10010060': good(10010060), '10034827': good(10034827) } }));
    const goods = await loadGoodsListGoods({ mode: 'byIds', goodsIds: [10034827, 10010060] });
    expect(goods.map(g => (g as { id: number }).id)).toEqual([10034827, 10010060]);
  });

  it('drops ids the backend omits (off-sale or deleted) instead of rendering holes', async () => {
    mockPost.mockReturnValue(Promise.resolve({ data: { '10034827': good(10034827) } }));
    const goods = await loadGoodsListGoods({ mode: 'byIds', goodsIds: [10034827, 999999] });
    expect(goods).toHaveLength(1);
  });

  it('asks for nothing when no ids are configured', async () => {
    await expect(loadGoodsListGoods({ mode: 'byIds', goodsIds: [] })).resolves.toEqual([]);
    expect(mockPost).not.toHaveBeenCalled();
  });

  it('keeps the LEADING picks when the rail shows fewer than the page does', async () => {
    const ids = [11, 12, 13, 14, 15];
    mockPost.mockReturnValue(Promise.resolve({ data: Object.fromEntries(ids.map(i => [String(i), good(i)])) }));
    const goods = await loadGoodsListGoods({ mode: 'byIds', goodsIds: ids }, 3);
    expect(goods.map(g => (g as { id: number }).id)).toEqual([11, 12, 13]);
    // Sliced after loading: the request still asked for every configured id.
    expect(mockPost).toHaveBeenCalledWith(expect.stringContaining('/goods/batch'), ids);
  });
});

describe('the other palette modes', () => {
  it('reads deals off the search envelope (goodsList, not list)', async () => {
    mockGet.mockReturnValue(envelope({ goodsList: [good(1)] }));
    await expect(loadGoodsListGoods({ mode: 'deals', limit: 4 })).resolves.toHaveLength(1);
    expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('/search'), { params: { deal_flag: 1, size: 4, page: 1 } });
  });

  it('reads byCategory / hot / new off the goods list', async () => {
    mockGet.mockReturnValue(envelope({ list: [good(2)] }));
    await loadGoodsListGoods({ mode: 'byCategory', categoryId: 1036143, limit: 6 });
    expect(mockGet).toHaveBeenCalledWith(expect.stringContaining('/goods/list'), { params: { categoryId: 1036143, limit: 6, page: 1 } });

    await loadGoodsListGoods({ mode: 'hot' });
    expect(mockGet).toHaveBeenLastCalledWith(expect.stringContaining('/goods/list'), { params: { isHot: true, limit: 8, page: 1 } });

    await loadGoodsListGoods({ mode: 'new' });
    expect(mockGet).toHaveBeenLastCalledWith(expect.stringContaining('/goods/list'), { params: { isNew: true, limit: 8, page: 1 } });
  });

  it('clamps limit to the palette bound of 24', async () => {
    mockGet.mockReturnValue(envelope({ goodsList: [] }));
    await loadGoodsListGoods({ mode: 'deals', limit: 500 });
    expect(mockGet).toHaveBeenCalledWith(expect.anything(), { params: { deal_flag: 1, size: 24, page: 1 } });
  });

  it('returns nothing for a mode it does not know, rather than guessing', async () => {
    await expect(loadGoodsListGoods({ mode: 'astrology' })).resolves.toEqual([]);
    expect(mockGet).not.toHaveBeenCalled();
    expect(mockPost).not.toHaveBeenCalled();
  });
});
