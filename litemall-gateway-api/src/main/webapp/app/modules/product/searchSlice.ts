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
  if (params.q) qs.set('q', params.q);
  if (params.category != null) qs.set('category', String(params.category));
  if (params.brands && params.brands.length) qs.set('brand', params.brands.join(','));
  if (params.minPrice != null) qs.set('minPrice', String(params.minPrice));
  if (params.maxPrice != null) qs.set('maxPrice', String(params.maxPrice));
  qs.set('page', String(params.page ?? 1));
  qs.set('size', String(params.size ?? 12));
  return qs.toString();
};

export const searchProducts = createAsyncThunk<ISearchResult, ISearchParams, { rejectValue: ApiResult<null> }>(
  'search/products',
  async (params, thunkApi) => {
    try {
      const response = await baseAxios.get(`${BASE_URL_CONTEXT}/search?${buildQuery(params)}`);
      if (response.data.errno !== 0) {
        return thunkApi.rejectWithValue({ errno: response.data.errno, errmsg: response.data.errmsg, data: null });
      }
      const d = response.data.data ?? {};
      // Tolerate either the agreed shape or a partial one while goods-management
      // finishes surfacing facets — never throw on a missing bucket.
      return {
        list: d.list ?? [],
        total: d.total ?? 0,
        page: d.page ?? params.page ?? 1,
        size: d.size ?? d.limit ?? params.size ?? 12,
        pages: d.pages ?? 0,
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
