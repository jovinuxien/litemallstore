import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { ADMIN_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { ApiResult, BaseState } from 'app/config/types';
import { IGroupons } from 'app/shared/model/groupons.model';

interface AdminCatoryResult
  extends ApiResult<{
    total: number | null;
    pages: number | null;
    limit: number | null;
    page: number | null;
    list: IGroupons[];
  }> {}

export const getAdminCategoryList = createAsyncThunk<AdminCatoryResult['data'], void, { rejectValue: ApiResult<null> }>(
  'admingoods/list',
  async (_, thunkApi) => {
    const adminToken = sessionStorage.getItem('adminToken');
    if (adminToken !== null) {
      try {
        const CatalogUrl = ADMIN_URL_CONTEXT + '/goods/list';
        const response = await baseAxios.get(CatalogUrl, { headers: { 'X-Litemall-Admin-Token': adminToken } });
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

interface AdminCategoryState
  extends BaseState<{
    adminCategoryResult: AdminCatoryResult['data'];
  }> {}

const initialState: AdminCategoryState = {
  loading: 'idle',
  errorMessage: null,
  data: {
    adminCategoryResult: {
      total: null,
      pages: null,
      limit: null,
      page: null,
      list: [],
    },
  },
  errorNumber: null,
};

const adminGoodsSlice = createSlice({
  name: 'categoryState',
  initialState,
  reducers: {},
  extraReducers: builder => {
    builder.addCase(getAdminCategoryList.fulfilled, (state, action) => {
      state.loading = 'succeeded';
      state.data.adminCategoryResult = action.payload;
    });
  },
});

export default adminGoodsSlice.reducer;
