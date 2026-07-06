import { IGood } from 'app/shared/model/product/product.model';

/**
 * OCS faceted-search contract surfaced by goods-management's `GET /srv/search`
 * (see SearchService). The OCS searcher returns facet buckets, sort options and
 * the applied-filter set per request; goods-management maps them onto a generic,
 * field-driven shape so new facets (brand, price, category, product attributes,
 * variants…) need no SPA change — the sidebar renders whatever `filters[]`
 * groups come back.
 *
 * Request params: `q`, `page` (1-based), `size`, `sort` (`field` asc / `-field`
 * desc), and one query param per active filter keyed by the OCS facet field
 * (e.g. `category_ids=5`, `brand=Acme,Globex`, `price=10,50`). The backend
 * whitelists filter keys to the index's facet fields and ignores the rest.
 */
export interface IFacetEntry {
  // The OCS term value to send back as the filter value (e.g. a brand name or a
  // category id). `id` is present when the facet field carries a numeric id.
  value: string;
  id?: number;
  count: number;
  selected: boolean;
}

export interface IFacetGroup {
  field: string; // OCS facet field, e.g. 'category_ids', 'brand', 'price'
  type: string; // 'term' | 'interval' (price) | …
  entries: IFacetEntry[];
}

export interface ISortOption {
  label: string;
  value: string; // what to send back as `sort` ('field' asc, '-field' desc)
  active: boolean;
}

export interface ISearchResult {
  list: IGood[];
  total: number;
  page: number;
  size: number;
  pages: number;
  facetGroups: IFacetGroup[];
  sortOptions: ISortOption[];
  // field -> value the backend actually applied (subset of the request filters).
  appliedFilters: Record<string, string>;
}

export interface ISearchParams {
  q?: string;
  page?: number;
  size?: number;
  sort?: string | null;
  // OCS facet field -> filter value (comma-joined for multi-select term facets,
  // 'min,max' for an interval facet such as price).
  filters?: Record<string, string>;
}

export const emptyResult: ISearchResult = {
  list: [],
  total: 0,
  page: 1,
  size: 12,
  pages: 0,
  facetGroups: [],
  sortOptions: [],
  appliedFilters: {},
};
