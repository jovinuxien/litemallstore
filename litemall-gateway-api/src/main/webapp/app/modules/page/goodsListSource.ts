import { baseAxios, SRV, unwrap } from 'app/shared/api';
import { IGood } from 'app/shared/model/product/product.model';

/**
 * Resolves the products behind ONE palette `goods-list` component
 * (spec-page-palette-v1.md §2.3).
 *
 * Extracted from `PageRenderer` in Wave 27 so the home season rail and the
 * season's own page resolve the same component the same way: the rail shows
 * the first few of exactly what the page shows, and the two can never
 * disagree about which products a season contains.
 *
 * Envelope quirks that live here rather than at the call sites:
 *  - `POST /srv/goods/batch` (byIds) answers a RAW `{goodsId: aggregate}` map
 *    with NO envelope; missing or off-sale ids are simply absent, and the
 *    CONFIGURED ORDER is what the admin curated, so it is preserved here.
 *  - `deals` reads the search envelope (`goodsList`, not `list`).
 */

/** Palette bound: 1–24 goods per component (server-side validator). */
const MAX_ITEMS = 24;

const clampLimit = (raw: unknown): number => Math.min(Math.max(Number(raw) || 8, 1), MAX_ITEMS);

export const loadGoodsListGoods = async (config: Record<string, unknown>, maxItems: number = MAX_ITEMS): Promise<IGood[]> => {
  const mode = config.mode as string;
  const limit = clampLimit(config.limit);

  const load = async (): Promise<IGood[]> => {
    if (mode === 'byIds') {
      const ids = (config.goodsIds as number[] | undefined) ?? [];
      if (ids.length === 0) return [];
      const map = await unwrap<Record<string, IGood>>(baseAxios.post(`${SRV}/goods/batch`, ids));
      if (!map || typeof map !== 'object') return [];
      return ids.map(id => map[String(id)]).filter(Boolean) as IGood[];
    }
    if (mode === 'deals') {
      // Scored browse: deepest-discount × most-popular deals first.
      const res =
        (await unwrap<{ goodsList?: IGood[] }>(baseAxios.get(`${SRV}/search`, { params: { deal_flag: 1, size: limit, page: 1 } }))) ?? {};
      return res.goodsList ?? [];
    }
    const params =
      mode === 'byCategory'
        ? { categoryId: config.categoryId as number, limit, page: 1 }
        : mode === 'hot'
          ? { isHot: true, limit, page: 1 }
          : mode === 'new'
            ? { isNew: true, limit, page: 1 }
            : null;
    if (!params) return [];
    const res = (await unwrap<{ list?: IGood[] }>(baseAxios.get(`${SRV}/goods/list`, { params }))) ?? {};
    return res.list ?? [];
  };

  // Sliced AFTER loading, so a byIds rail keeps the admin's leading picks.
  return (await load()).slice(0, Math.max(1, maxItems));
};

export default loadGoodsListGoods;
