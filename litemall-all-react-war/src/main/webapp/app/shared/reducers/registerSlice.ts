import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { ApiResult, BaseState } from 'app/config/types';
import { IUserInfo } from '../model/user.models';

interface UserInfoRegisterCall {
  nickname?: string;
  avatarUrl?: string;
  country?: string;
  province?: string;
  city?: string;
  language?: string;
  gender?: number;
}

interface Credentials {
  username: string;
  mobile: string;
  code: string;
  password: string;
  passwordConfirm: string;
  wxCode?: string;
}

export interface RegisterResult
  extends ApiResult<{
    token: string | null;
    user: UserInfoRegisterCall;
  }> {}

export const registerThunk = createAsyncThunk(
  'auth/register',
  async ({ username, password, mobile, code, wxCode }: Credentials, { rejectWithValue, dispatch, getState }) => {
    try {
      const registerUrl = BASE_URL_CONTEXT + '/auth/register';
      const response = await baseAxios.post<RegisterResult>(registerUrl, {
        username,
        password,
        mobile,
        code,
        wxCode,
      });

      return response.data;
    } catch (error) {
      /* return rejectWithValue((error as Error).message || 'Register request failed'); */
      return rejectWithValue({
        errno: -1,
        errmsg: error.response?.data?.message || 'Login failed',
        data: null,
      });
    }
  }
);

export interface RegisterState
  extends BaseState<{
    token: string;
    userInfo: IUserInfo;
    error: string;
    isAuthenticated: boolean;
  }> {}

const initialState: RegisterState = {
  loading: 'idle',
  errorMessage: null,
  data: {
    token: sessionStorage.getItem('token') || null,
    userInfo: {
      nickname: '',
      avatarUrl: '',
      country: '',
      province: '',
      city: '',
      language: '',
      gender: 0,
    },
    error: '',
    isAuthenticated: !!sessionStorage.getItem('token'),
  },
  errorNumber: null,
};

const registerSlice = createSlice({
  name: 'register',
  initialState,
  reducers: {
    /* updateProfile() {},
    updatePassword() {}, */
  },
  extraReducers: builder => {
    builder
      // Login
      .addCase(registerThunk.pending, (state, action) => {
        state.loading = 'pending';
        state.data.token = null;
      })
      .addCase(registerThunk.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = action.error.code;
      })
      .addCase(registerThunk.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.data.userInfo.nickname = action.payload.data.user.nickname;
        state.data.isAuthenticated = !!action.payload?.data?.token;
      });
  },
});

/* export const { updatePassword, updateProfile } = registerSlice.actions; */

export default registerSlice.reducer;
