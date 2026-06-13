import type { SearchClient, SearchResponse } from 'instantsearch.js';

import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { goodId } from 'app/components/userComponents/card/ProductCard';

/**
 * Custom InstantSearch search client backed by goods-management's
 * `GET /srv/search` (gateway → goods-management → OCS), NOT a raw Elasticsearch
 * cluster. This is the seam that lets the react-instantsearch widget set sit on
 * top of the existing OCS-through-the-BFF path: every query is relayed by
 * `baseAxios` so the customer JWT travels exactly as it does for the rest of the
 * SPA, and OCS stays the brain (relevance, facets, typo-tolerance).
 *
 * It translates the Algolia request model the widgets emit into the
 * `/srv/search` query contract, and maps the goods-management response back into
 * the Algolia `SearchResponse` shape the widgets consume:
 *
 *   query                     -> q
 *   page (0-based)            -> page (1-based)
 *   hitsPerPage               -> size  (+ offset/limit for the older backend)
 *   facetFilters "f:v"        -> f=v1,v2  (comma multi-select per OCS field)
 *   numericFilters "price>=x" -> price=min,max
 *   indexName ".../sort/<s>"  -> sort=<s>  ('field' asc, '-field' desc)
 *
 *   goodsList -> hits (objectID = goods id)
 *   total     -> nbHits        totalPages -> nbPages       limit -> hitsPerPage
 *   filters[] -> facets{field:{value:count}} (+ facets_stats for the price range)
 *
 * Facet population depends on goods-management actually returning `filters[]`
 * buckets; if it returns none, hits/sort/paging still work and the refinement
 * widgets simply stay empty (a goods-management follow-up, not fixable here).
 */

// The single OCS index. Sort variants are encoded as a virtual index suffix so
// the <SortBy> widget can switch sort order through the standard Algolia model.
export const PRIMARY_INDEX = 'litemall_index';
const SORT_SEP = '/sort/';

export const sortIndex = (sort: string): string => `${PRIMARY_INDEX}${SORT_SEP}${sort}`;

const parseSort = (indexName: string): string | null => {
  const i = indexName.indexOf(SORT_SEP);
  return i === -1 ? null : indexName.slice(i + SORT_SEP.length) || null;
};

type AlgoliaParams = {
  query?: string;
  page?: number;
  hitsPerPage?: number;
  facetFilters?: string | readonly (string | readonly string[])[];
  numericFilters?: string | readonly string[];
};

// Flatten Algolia's facetFilters (string | string[] | string[][]) into a flat
// list of "field:value" tokens. We treat everything as an AND of OR-groups but
// fold same-field values into one comma list, which is how OCS multi-select
// works on a single facet field.
const flattenFacetFilters = (ff: AlgoliaParams['facetFilters']): string[] => {
  if (ff == null) return [];
  if (typeof ff === 'string') return [ff];
  const out: string[] = [];
  ff.forEach(group => {
    if (typeof group === 'string') out.push(group);
    else group.forEach(v => out.push(v));
  });
  return out;
};

// "brand:Acme" / "category_ids:5" -> { brand: ['Acme'], category_ids: ['5'] }.
// A leading '-' (negation) is not expressible against OCS here, so we drop it.
const facetFiltersToFields = (tokens: string[]): Record<string, string[]> => {
  const fields: Record<string, string[]> = {};
  tokens.forEach(tok => {
    const idx = tok.indexOf(':');
    if (idx === -1) return;
    let field = tok.slice(0, idx);
    const value = tok.slice(idx + 1);
    if (field.startsWith('-')) field = field.slice(1); // ignore negation
    if (!value) return;
    (fields[field] ??= []).push(value);
  });
  return fields;
};

// numericFilters such as ["price>=10","price<=50"] -> { price: [10, 50] }.
// Only the closed-interval lower/upper bounds are mapped (OCS price=min,max).
const numericToPrice = (nf: AlgoliaParams['numericFilters']): [string, string] | null => {
  const list = nf == null ? [] : typeof nf === 'string' ? [nf] : [...nf];
  let lo: string | null = null;
  let hi: string | null = null;
  list.forEach(expr => {
    const m = /^([a-zA-Z0-9_]+)\s*(<=|>=|<|>|=)\s*(-?\d+(?:\.\d+)?)$/.exec(expr);
    if (!m || m[1] !== 'price') return;
    const [, , op, num] = m;
    if (op === '>=' || op === '>') lo = num;
    else if (op === '<=' || op === '<') hi = num;
    else if (op === '=') {
      lo = num;
      hi = num;
    }
  });
  if (lo == null && hi == null) return null;
  return [lo ?? '0', hi ?? '999999'];
};

const buildQuery = (indexName: string, params: AlgoliaParams): string => {
  const qs = new URLSearchParams();
  qs.set('q', params.query ?? '');
  const size = params.hitsPerPage ?? 12;
  const page = (params.page ?? 0) + 1; // Algolia is 0-based; backend is 1-based.
  qs.set('page', String(page));
  qs.set('size', String(size));
  // Older backend pages by offset/limit — send both so this degrades cleanly.
  qs.set('offset', String((page - 1) * size));
  qs.set('limit', String(size));

  const sort = parseSort(indexName);
  if (sort) qs.set('sort', sort);

  // Term facets -> one comma-joined param per OCS field.
  const fields = facetFiltersToFields(flattenFacetFilters(params.facetFilters));
  Object.entries(fields).forEach(([field, values]) => {
    if (field === 'price') return; // price comes from numericFilters
    if (values.length) qs.set(field, values.join(','));
  });

  // Price interval -> price=min,max.
  const price = numericToPrice(params.numericFilters);
  if (price) qs.set('price', `${price[0]},${price[1]}`);

  return qs.toString();
};

// goods-management facet groups (filters[]) -> Algolia facets + facets_stats.
const mapFacets = (
  d: any
): { facets: Record<string, Record<string, number>>; facetsStats: Record<string, { min: number; max: number }> } => {
  const groups = d?.filters ?? d?.facetGroups ?? d?.facets;
  const facets: Record<string, Record<string, number>> = {};
  const facetsStats: Record<string, { min: number; max: number }> = {};
  if (!Array.isArray(groups)) return { facets, facetsStats };
  groups.forEach((g: any) => {
    const field: string = g.field ?? g.fieldName ?? '';
    if (!field) return;
    const entries: any[] = Array.isArray(g.entries) ? g.entries : [];
    const isInterval = g.type === 'interval' || field === 'price';
    if (isInterval) {
      // <RangeInput> can only refine once it has numeric [min,max] bounds
      // (facets_stats). goods-management returns interval facets as DISPLAY
      // BUCKETS — strings like "< 79.99€", "80€ - 119.99€", "> 915€" — with no
      // numeric fields, so Number(entry.value) is NaN and the slider stays dead.
      // Parse the numbers out of every bucket label to recover global bounds:
      // an open-low bucket ("< N" / "≤ N") floors the range at 0; an open-high
      // bucket ("> N") contributes N. Still prefer explicit numeric fields if a
      // future backend provides them (g.min/g.max, entry.min/max/from/to).
      const bounds: number[] = [];
      entries.forEach((e: any) => {
        [e.min, e.max, e.from, e.to].forEach((n: any) => {
          const x = Number(n);
          if (Number.isFinite(x)) bounds.push(x);
        });
        const label = String(e.value ?? '');
        const nums = (label.match(/\d+(?:[.,]\d+)?/g) ?? []).map(s => Number(s.replace(',', '.'))).filter(n => Number.isFinite(n));
        if (!nums.length) return;
        if (/^\s*[<≤]/.test(label)) bounds.push(0); // "< N" -> items down to 0
        bounds.push(...nums);
      });
      const min = Number(g.min ?? (bounds.length ? Math.min(...bounds) : NaN));
      const max = Number(g.max ?? (bounds.length ? Math.max(...bounds) : NaN));
      if (Number.isFinite(min) && Number.isFinite(max) && max > min) {
        facetsStats[field] = { min: Math.floor(min), max: Math.ceil(max) };
        // InstantSearch's helper only attaches facet stats to a facet it created
        // from the response `facets` map — without a `facets[field]` entry it
        // skips the facet and getFacetStats() returns nothing, leaving the
        // RangeInput bounds at 0/0. Expose the buckets (value:count) so the
        // facet exists and the parsed [min,max] stats reach the widget.
        const bucket: Record<string, number> = {};
        entries.forEach((e: any) => {
          const value = String(e.value ?? '');
          if (value) bucket[value] = Number(e.count ?? e.docCount ?? 0) || 0;
        });
        facets[field] = bucket;
      }
      return;
    }
    const bucket: Record<string, number> = {};
    entries.forEach((e: any) => {
      const value = String(e.value ?? e.key ?? e.id ?? '');
      if (value) bucket[value] = Number(e.count ?? e.docCount ?? 0) || 0;
    });
    if (Object.keys(bucket).length) facets[field] = bucket;
  });
  return { facets, facetsStats };
};

const emptyResponse = (indexName: string, params: AlgoliaParams): SearchResponse<any> => ({
  hits: [],
  nbHits: 0,
  page: params.page ?? 0,
  nbPages: 0,
  hitsPerPage: params.hitsPerPage ?? 12,
  facets: {},
  query: params.query ?? '',
  params: '',
  processingTimeMS: 0,
  index: indexName,
  exhaustiveNbHits: true,
});

const runSearch = async (indexName: string, params: AlgoliaParams): Promise<SearchResponse<any>> => {
  const size = params.hitsPerPage ?? 12;
  try {
    const response = await baseAxios.get(`${BASE_URL_CONTEXT}/search?${buildQuery(indexName, params)}`);
    const body = response.data ?? {};
    if (body.errno != null && body.errno !== 0) return emptyResponse(indexName, params);
    const d = body.data ?? body; // tolerate the {errno,data} envelope or a raw map

    const list: any[] = d.goodsList ?? d.list ?? [];
    const total = Number(d.total ?? 0) || 0;
    const hitsPerPage = Number(d.limit ?? d.size ?? size) || size;
    const page = (Number(d.page ?? (params.page ?? 0) + 1) || 1) - 1;
    const nbPages = Number(d.totalPages ?? d.pages ?? (hitsPerPage > 0 ? Math.ceil(total / hitsPerPage) : 0)) || 0;
    const { facets, facetsStats } = mapFacets(d);

    return {
      hits: list.map((g, i) => ({ ...g, objectID: String(goodId(g) ?? g.id ?? `r-${i}`) })),
      nbHits: total,
      page,
      nbPages,
      hitsPerPage,
      facets,
      ...(Object.keys(facetsStats).length ? { facets_stats: facetsStats } : {}),
      query: params.query ?? '',
      params: '',
      processingTimeMS: 0,
      index: indexName,
      exhaustiveNbHits: true,
    };
  } catch {
    // Network/timeout: return an empty page rather than throwing so the UI shows
    // "no results" instead of crashing the InstantSearch tree.
    return emptyResponse(indexName, params);
  }
};

/**
 * The object handed to <InstantSearch searchClient>. InstantSearch batches all
 * widget queries for a render into one `search(requests)` call; we resolve each
 * request against `/srv/search` independently (typically just one for the single
 * OCS index). `searchForFacetValues` is a no-op — we don't enable searchable
 * facets, but the method must exist so widgets that probe for it don't throw.
 */
export const litemallSearchClient: SearchClient = {
  search(requests: any) {
    return Promise.all(requests.map((r: any) => runSearch(r.indexName, r.params ?? {}))).then(results => ({ results }));
  },
  searchForFacetValues() {
    return Promise.resolve([{ facetHits: [], exhaustiveFacetsCount: true, processingTimeMS: 0 }]);
  },
} as unknown as SearchClient;

export default litemallSearchClient;
