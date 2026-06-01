import { IGood } from 'app/shared/model/product/product.model';

/**
 * Agreed OCS faceted-search contract surfaced by goods-management's
 * `GET /srv/search` (see SearchService). The OCS searcher already returns facet
 * buckets per request; goods-management currently DROPS them when mapping to
 * the goodsList DTO. Until that worktree surfaces them, the sidebar codes
 * against this shape. FOLLOW-UP(goods-management): include `facets` (category +
 * brand buckets, overall price range) in the `/srv/search` response `data`.
 *
 * Request params: q, category (id), brand (csv of ids), minPrice, maxPrice,
 * page (1-based), size. The gateway maps page/size to OCS offset/limit.
 */
export interface IFacetBucket {
  id: number;
  name: string;
  count: number;
}

export interface IPriceRange {
  min: number;
  max: number;
}

export interface ISearchFacets {
  categories: IFacetBucket[];
  brands: IFacetBucket[];
  price: IPriceRange | null;
}

export interface ISearchResult {
  list: IGood[];
  total: number;
  page: number;
  size: number;
  pages: number;
  facets: ISearchFacets;
}

export interface ISearchParams {
  q?: string;
  category?: number | null;
  brands?: number[];
  minPrice?: number | null;
  maxPrice?: number | null;
  page?: number;
  size?: number;
}

export const emptyFacets: ISearchFacets = { categories: [], brands: [], price: null };
