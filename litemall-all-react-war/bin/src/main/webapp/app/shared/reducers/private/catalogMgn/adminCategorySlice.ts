import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { ADMIN_URL_CONTEXT } from 'app/config/api';
import { ApiResult, BaseState } from 'app/config/types';
import { CategoryData } from 'app/shared/model/category/category.models';
import axios from 'axios';

interface AdminCatoryResult
  extends ApiResult<{
    total: number | null;
    pages: number | null;
    limit: number | null;
    page: number | null;
    list: CategoryData[];
  }> {}

export const getAdminCategoryList = createAsyncThunk<AdminCatoryResult['data'], void, { rejectValue: ApiResult<null> }>(
  'adminCategory/list',
  async (_, thunkApi) => {
    const adminToken = sessionStorage.getItem('adminToken');
    if (adminToken !== null) {
      try {
        const CatalogUrl = ADMIN_URL_CONTEXT + '/category/list';
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

const adminCategorySlice = createSlice({
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

export default adminCategorySlice.reducer;
