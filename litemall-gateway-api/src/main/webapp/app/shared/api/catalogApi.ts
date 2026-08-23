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

export interface OriginResponse {
  list?: { goodsId?: number; originCountry?: string }[];
}

/**
 * Set once `/srv/goods/origin` answers "no such endpoint". Session-scoped and
 * in memory only — see the note on `goodsOrigin`.
 */
let originUnavailable = false;

/** Test seam — forgets that the origin endpoint was missing. */
export const __resetOriginAvailability = (): void => {
  originUnavailable = false;
};

export const catalogApi = {
  /** GET /srv/goods/index — home new/hot lists. */
  index: () => unwrap(baseAxios.get(`${SRV}/goods/index`)),
  /** GET /srv/goods/detail?id= — goods + products(SKU) + specifications + attributes + categoryIds. */
  goodsDetail: (id: string) => unwrap(baseAxios.get(`${SRV}/goods/detail?id=${encodeURIComponent(id)}`)),
  /** GET /srv/goods/related?id= — related products row. */
  related: (id: string) => unwrap(baseAxios.get(`${SRV}/goods/related?id=${encodeURIComponent(id)}`)),
  /** GET /srv/goods/meta/{id} — slim public meta (Wave-13 SEO contract): rating, reviewCount, categoryName…. Numeric ids only. */
  goodsMeta: (id: number | string) =>
    unwrap<{ rating?: number | null; reviewCount?: number; categoryId?: number; categoryName?: string }>(
      baseAxios.get(`${SRV}/goods/meta/${encodeURIComponent(String(id))}`)
    ),
  /** GET /srv/goods/list — flat SQL listing (used for hot/new/category quick lists). */
  goodsList: (params: GoodsListParams) => unwrap(baseAxios.get(`${SRV}/goods/list`, { params })),
  /**
   * GET /srv/goods/origin?ids= — Wave-28 batch warehouse-origin read.
   *
   * Returns a row ONLY for goods with a measured, non-zero EU warehouse stock;
   * unmeasured goods are absent from the list rather than carrying a null or a
   * guessed "CN". Public for the same reason the PDP's `euStock` key is public:
   * it is the same measurement, already served anonymously by /srv/goods/detail.
   */
  goodsOrigin: async (ids: (number | string)[]): Promise<OriginResponse> => {
    // The Wave-28 SPA shipped before its backend half, so on today's production
    // this path 404s — verified 2026-08-23. The call is fail-open by design and
    // the checkout is unaffected, but re-asking an endpoint that does not exist
    // on every cart change is a guaranteed-failing request per checkout load.
    //
    // A "not built" answer (404/501) is remembered for the session and further
    // calls short-circuit. Only those two statuses: a 5xx or a dropped
    // connection is the endpoint being ill, not absent, and must stay retryable.
    // Nothing is persisted, so the day the backend half ships every new session
    // picks it up with no rebuild and no flag to flip.
    if (originUnavailable) return { list: [] };
    try {
      return await unwrap<OriginResponse>(baseAxios.get(`${SRV}/goods/origin`, { params: { ids: ids.join(',') } }));
    } catch (e) {
      const status = (e as { response?: { status?: number } })?.response?.status;
      if (status === 404 || status === 501) originUnavailable = true;
      throw e;
    }
  },
  /** GET /srv/catalog/all — full category tree (mega menu). */
  catalogAll: () => unwrap(baseAxios.get(`${SRV}/catalog/all`)),
  /** GET /srv/catalog/current?id= — one category branch. */
  catalogCurrent: (id: number | string) => unwrap(baseAxios.get(`${SRV}/catalog/current?id=${encodeURIComponent(String(id))}`)),
  /** GET /srv/suggest?q= — search autocomplete (raw array). */
  suggest: (q: string) => unwrap(baseAxios.get(`${SRV}/suggest?q=${encodeURIComponent(q)}`)),
};
