import { baseAxios, SRV, unwrap } from './http';

/**
 * Public catalog/goods/search reads — all live on `/srv` today
 * (litemall-goods-management, `/srv/**`). No `/wx`.
 */
export interface GoodsListParams {
  categoryId?: number | string;
  brandId?: number | string;
  keyword?: string;
  isNew?: boolean;
  isHot?: boolean;
  page?: number;
  limit?: number;
  sort?: string;
  order?: 'asc' | 'desc';
}

export const catalogApi = {
  /** GET /srv/goods/index — home new/hot lists. */
  index: () => unwrap(baseAxios.get(`${SRV}/goods/index`)),
  /** GET /srv/goods/detail?id= — goods + products(SKU) + specifications + attributes + categoryIds. */
  goodsDetail: (id: string) => unwrap(baseAxios.get(`${SRV}/goods/detail?id=${encodeURIComponent(id)}`)),
  /** GET /srv/goods/related?id= — related products row. */
  related: (id: string) => unwrap(baseAxios.get(`${SRV}/goods/related?id=${encodeURIComponent(id)}`)),
  /** GET /srv/goods/list — flat SQL listing (used for hot/new/category quick lists). */
  goodsList: (params: GoodsListParams) => unwrap(baseAxios.get(`${SRV}/goods/list`, { params })),
  /** GET /srv/catalog/all — full category tree (mega menu). */
  catalogAll: () => unwrap(baseAxios.get(`${SRV}/catalog/all`)),
  /** GET /srv/catalog/current?id= — one category branch. */
  catalogCurrent: (id: number | string) => unwrap(baseAxios.get(`${SRV}/catalog/current?id=${encodeURIComponent(String(id))}`)),
  /** GET /srv/suggest?q= — search autocomplete (raw array). */
  suggest: (q: string) => unwrap(baseAxios.get(`${SRV}/suggest?q=${encodeURIComponent(q)}`)),
};
