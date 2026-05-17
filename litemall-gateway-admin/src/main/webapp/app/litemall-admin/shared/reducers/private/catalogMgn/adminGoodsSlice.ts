import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { ADMIN_URL_CONTEXT } from 'app/config/api';
import { ApiResult, BaseState } from 'app/config/types';
import { IBrandData } from 'app/shared/model/brand.model';
import { CategoryData } from 'app/shared/model/category/category.models';
import { IGood } from 'app/shared/model/product/product.model';
import axios from 'axios';

interface AdminGoodsResult
  extends ApiResult<{
    total: number | null;
    pages: number | null;
    limit: number | null;
    page: number | null;
    list: IGood[];
  }> {}

interface AdminGoodsCatAndBrandResult
  extends ApiResult<{
    catList: CategoryData[];
    brandList: IBrandData[];
  }> {}

export const getAdminGoodsList = createAsyncThunk<
  AdminGoodsResult['data'],
  { limit: number; page: number; sort: string; order: 'desc' | 'asc' },
  { rejectValue: ApiResult<null> }
>('admingoods/list', async (params, thunkApi) => {
  const adminToken = sessionStorage.getItem('adminToken');
  if (adminToken !== null) {
    try {
      const CatalogUrl = `${ADMIN_URL_CONTEXT}/goods/list?limit=${params.limit}&page=${params.page}&sort=${params.sort}&order=${params.order}`;
      const response = await axios.get(CatalogUrl, { headers: { 'X-Litemall-Admin-Token': adminToken } });
      if (response.data.errno !== 0) {
        return thunkApi.rejectWithValue({
          errno: response.data.errno,
          errmsg: response.data.errmsg,
          data: null,
        });
      }
      return response.data.data;
    } catch (error) {
      return thunkApi.rejectWithValue({
        errno: 500,
        errmsg: error.message,
        data: null,
      });
    }
  } else {
    return thunkApi.rejectWithValue({
      errno: -1,
      errmsg: 'You need to login first',
      data: null,
    });
  }
});

export const getAdminGoodsCatAndBrand = createAsyncThunk<AdminGoodsCatAndBrandResult['data'], void, { rejectValue: ApiResult<null> }>(
  'catandbrandadmingoods/list',
  async (_, thunkApi) => {
    const adminToken = sessionStorage.getItem('adminToken');
    if (adminToken !== null) {
      try {
        const CatalogUrl = `${ADMIN_URL_CONTEXT}/goods/catAndBrand`;
        const response = await axios.get(CatalogUrl, { headers: { 'X-Litemall-Admin-Token': adminToken } });
        if (response.data.errno !== 0) {
          return thunkApi.rejectWithValue({
            errno: response.data.errno,
            errmsg: response.data.errmsg,
            data: null,
          });
        }
        return response.data.data;
      } catch (error) {
        return thunkApi.rejectWithValue({
          errno: 500,
          errmsg: error.message,
          data: null,
        });
      }
    } else {
      return thunkApi.rejectWithValue({
        errno: -1,
        errmsg: 'You need to login first',
        data: null,
      });
    }
  }
);
interface AdminGoodsState
  extends BaseState<{
    adminGoodsResultList: AdminGoodsResult['data'];
    adminGoodsCatAndBrandResult: AdminGoodsCatAndBrandResult['data'];
  }> {}

const initialState: AdminGoodsState = {
  loading: 'idle',
  errorMessage: null,
  data: {
    adminGoodsResultList: {
      total: null,
      pages: null,
      limit: null,
      page: null,
      list: [],
    },
    adminGoodsCatAndBrandResult: {
      catList: [],
      brandList: [],
    },
  },
  errorNumber: null,
};

const adminGoodsSlice = createSlice({
  name: 'categoryState',
  initialState,
  reducers: {},
  extraReducers: builder => {
    builder.addCase(getAdminGoodsList.fulfilled, (state, action) => {
      state.loading = 'succeeded';
      state.data.adminGoodsResultList = action.payload;
    });
    builder.addCase(getAdminGoodsCatAndBrand.fulfilled, (state, action) => {
      state.loading = 'succeeded';
      state.data.adminGoodsCatAndBrandResult = action.payload;
    });
  },
});

//export const {} = homeSlice.actions;
export default adminGoodsSlice.reducer;
