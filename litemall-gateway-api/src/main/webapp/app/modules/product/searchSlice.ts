import { createAsyncThunk, createSlice, PayloadAction } from '@reduxjs/toolkit';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { ApiResult, BaseState } from 'app/config/types';
import { emptyResult, IFacetGroup, ISearchParams, ISearchResult, ISortOption } from 'app/shared/model/search/search.models';

/**
 * OCS-backed faceted listing. Drives the product page that the home
 * category / hot-deal tiles and the header search box route to. Hits
 * goods-management's `GET /srv/search` through the gateway (customer JWT relayed
 * by baseAxios). No SQL fallback — the faceted view is OCS-only by design.
 *
 * Contract (goods-management SearchService): request `q,page,size,sort` plus one
 * param per active facet filter keyed by the OCS field (`category_ids`, `brand`,
 * `price=min,max`, …); response `data` carries `goodsList, total, totalPages,
 * page, limit, filters[], sortOptions[], appliedFilters`. We also send
 * `offset/limit` and read `goodsList`/raw shapes so this still degrades cleanly
 * against the older backend that only did free-text `q` + offset paging.
 */
const buildQuery = (params: ISearchParams): string => {
  const qs = new URLSearchParams();
  qs.set('q', params.q ?? '');
  const size = params.size ?? 12;
  const page = params.page ?? 1;
  // New backend pages by page/size; older one by offset/limit — send both.
  qs.set('page', String(page));
  qs.set('size', String(size));
  qs.set('offset', String((page - 1) * size));
  qs.set('limit', String(size));
  if (params.sort) qs.set('sort', params.sort);
  // One query param per active facet filter, keyed by the OCS facet field.
  if (params.filters) {
    Object.entries(params.filters).forEach(([field, value]) => {
      if (value != null && String(value).trim() !== '') qs.set(field, String(value));
    });
  }
  return qs.toString();
};

// The backend returns facet groups as `filters[]`; tolerate a couple of key
// spellings so we don't depend on one exact field name.
const readFacetGroups = (d: any): IFacetGroup[] => {
  const raw = d?.filters ?? d?.facetGroups ?? d?.facets;
  if (!Array.isArray(raw)) return [];
  return raw.map((g: any) => ({
    field: g.field ?? g.fieldName ?? '',
    type: g.type ?? 'term',
    entries: Array.isArray(g.entries)
      ? g.entries.map((e: any) => ({
          value: String(e.value ?? e.key ?? e.id ?? ''),
          id: e.id ?? undefined,
          count: Number(e.count ?? e.docCount ?? 0) || 0,
          selected: Boolean(e.selected),
        }))
      : [],
  }));
};

export const readSortOptions = (d: any): ISortOption[] => {
  const raw = d?.sortOptions;
  if (!Array.isArray(raw)) return [];
  return raw.map((o: any) => ({
    label: String(o.label ?? o.value ?? ''),
    value: String(o.value ?? ''),
    active: Boolean(o.active),
  }));
};

export const searchProducts = createAsyncThunk<ISearchResult, ISearchParams, { rejectValue: ApiResult<null> }>(
  'search/products',
  async (params, thunkApi) => {
    try {
      const response = await baseAxios.get(`${BASE_URL_CONTEXT}/search?${buildQuery(params)}`);
      // Tolerate both the enveloped `{errno,data}` shape and a raw map.
      const body = response.data ?? {};
      if (body.errno != null && body.errno !== 0) {
        return thunkApi.rejectWithValue({ errno: body.errno, errmsg: body.errmsg, data: null });
      }
      const d = body.data ?? body;
      const size = params.size ?? 12;
      const page = params.page ?? 1;
      const total = Number(d.total ?? 0) || 0;
      const pages = d.totalPages ?? d.pages ?? (size > 0 ? Math.ceil(total / size) : 0);
      return {
        list: d.goodsList ?? d.list ?? [],
        total,
        page: d.page ?? page,
        size: d.limit ?? d.size ?? size,
        pages,
        facetGroups: readFacetGroups(d),
        sortOptions: readSortOptions(d),
        appliedFilters: (d.appliedFilters && typeof d.appliedFilters === 'object') ? d.appliedFilters : {},
      };
    } catch (error) {
      return thunkApi.rejectWithValue({ errno: 500, errmsg: error.message, data: null });
    }
  }
);

/**
 * Per-response search metadata the faceted /search page reads OUTSIDE the
 * InstantSearch widget tree: the server-driven sort options, and the
 * relaxed-match signal ("no exact matches — showing similar results"). The
 * custom search client (litemallSearchClient) dispatches this for every
 * /srv/search response it maps — the widgets consume hits/facets through the
 * Algolia shape, and this slice carries the fields that shape has no slot for.
 */
export interface ISearchMeta {
  query: string;
  total: number;
  queryStrategy: string | null;
  relaxed: boolean;
  sortOptions: ISortOption[];
  // Wave-19: the backend's typed outage state (errno 502 "Search is temporarily
  // unavailable", or the gateway unreachable). The /search page renders an
  // honest outage message instead of the generic "no results" empty state.
  unavailable: boolean;
}

export const emptyMeta: ISearchMeta = {
  query: '',
  total: 0,
  queryStrategy: null,
  relaxed: false,
  sortOptions: [],
  unavailable: false,
};

interface SearchState extends BaseState<ISearchResult> {
  meta: ISearchMeta;
}

const initialState: SearchState = {
  loading: 'idle',
  errorMessage: null,
  errorNumber: null,
  data: emptyResult,
  meta: emptyMeta,
};

const searchSlice = createSlice({
  name: 'search',
  initialState,
  reducers: {
    searchMetaReceived(state, action: PayloadAction<ISearchMeta>) {
      state.meta = action.payload;
    },
  },
  extraReducers: builder => {
    builder
      .addCase(searchProducts.pending, state => {
        state.loading = 'pending';
        state.errorMessage = null;
      })
      .addCase(searchProducts.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.data = action.payload;
      })
      .addCase(searchProducts.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = action.payload?.errmsg ?? 'Search failed';
        state.errorNumber = action.payload?.errno ?? 500;
      });
  },
});

export const { searchMetaReceived } = searchSlice.actions;

export default searchSlice.reducer;
