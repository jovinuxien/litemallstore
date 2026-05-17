import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { ADMIN_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { ApiResult, BaseState } from 'app/config/types';
import { IGood } from 'app/shared/model/product/product.model';

interface AdminTopicCallResult
  extends ApiResult<{
    id: number;
    title: string;
    subtitle: string;
    price: number;
    readCount: string;
    picUrl: string;
    sortOrder: number;
    goods: IGood[];
    addTime: Date;
    updateTime: Date;
    deleted: false;
    content: string;
  }> {}

export const getAdminTopicThunk = createAsyncThunk<AdminTopicCallResult['data'], void, { rejectValue: ApiResult<null> }>(
  'adminGroupon/list',
  async (_, thunkApi) => {
    const adminToken = sessionStorage.getItem('adminToken');
    if (adminToken !== null) {
      try {
        const topicUrl = ADMIN_URL_CONTEXT + '/topic/list';
        const response = await baseAxios.get(topicUrl, { headers: { 'X-Litemall-Admin-Token': adminToken } });
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

interface AdminTopicState
  extends BaseState<{
    adminTopicResult: AdminTopicCallResult['data'];
  }> {}

const initialState: AdminTopicState = {
  loading: 'idle',
  data: {
    adminTopicResult: {
      id: 0,
      addTime: null,
      content: '',
      deleted: false,
      goods: [],
      picUrl: '',
      price: 0.0,
      readCount: '',
      sortOrder: 0,
      subtitle: '',
      title: '',
      updateTime: null,
    },
  },
  errorMessage: '',
  errorNumber: null,
};

const adminIssueSlice = createSlice({
  name: 'grouponState',
  initialState,
  reducers: {},
  extraReducers: builder => {
    builder
      .addCase(getAdminTopicThunk.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.data.adminTopicResult = action.payload;
      })
      .addCase(getAdminTopicThunk.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = action.error.message;
        state.errorNumber = parseInt(action.error.code);
      })
      .addCase(getAdminTopicThunk.pending, (state, action) => {
        state.loading = 'pending';
      });
  },
});
export default adminIssueSlice.reducer;
