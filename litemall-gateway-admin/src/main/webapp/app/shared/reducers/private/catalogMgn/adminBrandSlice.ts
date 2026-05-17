import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { ADMIN_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { ApiResult, BaseState } from 'app/config/types';

interface AdminBrandApiCallResult
  extends ApiResult<{
    id: number;
    name: string;
    desc: string;
    picUrl: string;
    sortOrder: number;
    floorPrice: number;
    addTime: Date;
    updateTime: Date;
    deleted: boolean;
  }> {}

export const getAdminBrandList = createAsyncThunk<AdminBrandApiCallResult['data'], void, { rejectValue: ApiResult<null> }>(
  'adminBrand/list',
  async (_, thunkApi) => {
    const adminToken = sessionStorage.getItem('adminToken');
    if (adminToken !== null) {
      try {
        const CatalogUrl = ADMIN_URL_CONTEXT + '/brand/list';
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

interface AdminBrandState
  extends BaseState<{
    adminBrandList: AdminBrandApiCallResult['data'];
  }> {}

const initialState: AdminBrandState = {
  loading: 'idle',
  errorMessage: null,
  data: {
    adminBrandList: {
      id: 0,
      name: '',
      desc: '',
      picUrl: '',
      sortOrder: 0,
      floorPrice: 0,
      addTime: new Date(),
      updateTime: new Date(),
      deleted: false,
    },
  },
  errorNumber: null,
};

const adminBrandSlice = createSlice({
  name: 'adminBrandState',
  initialState,
  reducers: {},
  extraReducers: builder => {
    builder
      .addCase(getAdminBrandList.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.data.adminBrandList = action.payload;
      })
      .addCase(getAdminBrandList.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = action.payload.errmsg;
        state.errorNumber = action.payload.errno;
      })
      .addCase(getAdminBrandList.pending, (state, action) => {
        state.loading = 'pending';
      });
  },
});

export default adminBrandSlice.reducer;
