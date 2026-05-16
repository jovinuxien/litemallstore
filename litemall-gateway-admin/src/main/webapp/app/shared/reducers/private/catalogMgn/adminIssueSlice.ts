import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { ADMIN_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { ApiResult, BaseState } from 'app/config/types';

interface AdminIssueCallResult
  extends ApiResult<{
    id: number;
    question: string;
    answer: string;
    addTime: Date;
    updateTime: Date;
    deleted: Date;
  }> {}

export const getAdminIssueThunk = createAsyncThunk<AdminIssueCallResult['data'], void, { rejectValue: ApiResult<null> }>(
  'adminGroupon/list',
  async (_, thunkApi) => {
    const adminToken = sessionStorage.getItem('adminToken');
    if (adminToken !== null) {
      try {
        const issueUrl = ADMIN_URL_CONTEXT + '/issue/list';
        const response = await baseAxios.get(issueUrl, { headers: { 'X-Litemall-Admin-Token': adminToken } });
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

interface AdminIssueState
  extends BaseState<{
    adminIssueResult: AdminIssueCallResult['data'];
  }> {}

const initialState: AdminIssueState = {
  loading: 'idle',
  data: {
    adminIssueResult: {
      id: null,
      addTime: null,
      answer: '',
      question: '',
      updateTime: null,
      deleted: null,
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
      .addCase(getAdminIssueThunk.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.data.adminIssueResult = action.payload;
      })
      .addCase(getAdminIssueThunk.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = action.error.message;
        state.errorNumber = parseInt(action.error.code);
      })
      .addCase(getAdminIssueThunk.pending, (state, action) => {
        state.loading = 'pending';
      });
  },
});
export default adminIssueSlice.reducer;
