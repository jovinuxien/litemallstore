import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { ApiResult, BaseState } from 'app/config/types';
import { emptyFacets, ISearchParams, ISearchResult } from 'app/shared/model/search/search.models';

/**
 * OCS-backed faceted listing. Drives the product page that the home
 * category / hot-deal tiles route to. Hits goods-management's
 * `GET /srv/search?q=&category=&brand=&minPrice=&maxPrice=&page=&size=` through
 * the gateway (customer JWT relayed by baseAxios). No SQL fallback — the
 * faceted view is OCS-only by design.
 */
const buildQuery = (params: ISearchParams): string => {
  const qs = new URLSearchParams();
  // q is a required param on /srv/search; always send it (possibly empty) so it binds.
  qs.set('q', params.q ?? '');
  // Facet filters — goods-management ignores these today; sent forward-compatibly
  // for when its OCS aggregations land. The facet sidebar stays wired meanwhile.
  if (params.category != null) qs.set('category', String(params.category));
  if (params.brands && params.brands.length) qs.set('brand', params.brands.join(','));
  if (params.minPrice != null) qs.set('minPrice', String(params.minPrice));
  if (params.maxPrice != null) qs.set('maxPrice', String(params.maxPrice));
  // Sort — forward-compatible; OCS default is relevance (no param). The Search
  // page also sorts client-side until goods-management honors this server-side.
  if (params.sort && params.sort !== 'relevance') qs.set('sort', params.sort);
  // /srv/search pages by offset/limit, not page/size.
  const size = params.size ?? 12;
  const page = params.page ?? 1;
  qs.set('offset', String((page - 1) * size));
  qs.set('limit', String(size));
  return qs.toString();
};

export const searchProducts = createAsyncThunk<ISearchResult, ISearchParams, { rejectValue: ApiResult<null> }>(
  'search/products',
  async (params, thunkApi) => {
    try {
      const response = await baseAxios.get(`${BASE_URL_CONTEXT}/search?${buildQuery(params)}`);
      // /srv/search returns a RAW map { total, offset, limit, goodsList } with no
      // {errno,data} envelope; tolerate both that and an enveloped shape.
      const body = response.data ?? {};
      if (body.errno != null && body.errno !== 0) {
        return thunkApi.rejectWithValue({ errno: body.errno, errmsg: body.errmsg, data: null });
      }
      const d = body.data ?? body;
      const size = params.size ?? 12;
      const page = params.page ?? 1;
      const total = d.total ?? 0;
      // facets stay defensive — empty until goods-management surfaces aggregations.
      return {
        list: d.goodsList ?? d.list ?? [],
        total,
        page,
        size,
        pages: d.pages ?? (size > 0 ? Math.ceil(total / size) : 0),
        facets: {
          categories: d.facets?.categories ?? [],
          brands: d.facets?.brands ?? [],
          price: d.facets?.price ?? null,
        },
      };
    } catch (error) {
      return thunkApi.rejectWithValue({ errno: 500, errmsg: error.message, data: null });
    }
  }
);

interface SearchState extends BaseState<ISearchResult> {}

const initialState: SearchState = {
  loading: 'idle',
  errorMessage: null,
  errorNumber: null,
  data: { list: [], total: 0, page: 1, size: 12, pages: 0, facets: emptyFacets },
};

const searchSlice = createSlice({
  name: 'search',
  initialState,
  reducers: {},
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

export default searchSlice.reducer;
