import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { ApiResult, BaseState, createApiClient } from '@litemall/shared';

/**
 * Customer auth, split out of the former combined authSlice (no more
 * loginAdminThunk here). Talks to the gateway-api edge same-origin: relative
 * /auth/* (Phase 2 contract — {errno,errmsg,data}, Bearer JWT). The admin
 * realm now lives entirely in the gateway-admin SPA.
 */
const TOKEN_KEY = 'customerToken';
const api = createApiClient({ tokenKey: TOKEN_KEY });

interface Credentials {
  username: string;
  password: string;
}

interface CustomerInfo {
  nickName: string;
  avatarUrl: string;
}

export interface CustomerAuthData {
  token: string | null;
  refreshToken: string | null;
  userInfo: CustomerInfo | null;
  isAuthenticated: boolean;
}

export const loginCustomerThunk = createAsyncThunk<
  ApiResult<{ token: string; refreshToken: string; userInfo: CustomerInfo }>,
  Credentials,
  { rejectValue: ApiResult<null> }
>('customerAuth/login', async ({ username, password }, thunkApi) => {
  try {
    // Raw post: the login response is the envelope itself, not unwrapped data.
    const res = await fetch('/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });
    const body = await res.json();
    if (body.errno !== 0) {
      return thunkApi.rejectWithValue({ errno: body.errno, errmsg: body.errmsg, data: null });
    }
    return body;
  } catch (e) {
    return thunkApi.rejectWithValue({ errno: -1, errmsg: 'Login failed', data: null });
  }
});

export const logoutCustomerThunk = createAsyncThunk('customerAuth/logout', async () => {
  const refreshToken = sessionStorage.getItem('customerRefreshToken');
  await api.post('/auth/logout', { refreshToken });
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem('customerRefreshToken');
});

const initialState: BaseState<CustomerAuthData> = {
  loading: 'idle',
  errorMessage: null,
  errorNumber: null,
  data: { token: null, refreshToken: null, userInfo: null, isAuthenticated: false },
};

const customerAuthSlice = createSlice({
  name: 'customerAuth',
  initialState,
  reducers: {},
  extraReducers: builder => {
    builder
      .addCase(loginCustomerThunk.pending, state => {
        state.loading = 'pending';
        state.errorMessage = null;
      })
      .addCase(loginCustomerThunk.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        const d = action.payload.data;
        state.data = { ...d, isAuthenticated: true };
        if (d.token) sessionStorage.setItem(TOKEN_KEY, d.token);
        if (d.refreshToken) sessionStorage.setItem('customerRefreshToken', d.refreshToken);
      })
      .addCase(loginCustomerThunk.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = action.payload?.errmsg ?? 'Login failed';
        state.errorNumber = action.payload?.errno ?? -1;
      })
      .addCase(logoutCustomerThunk.fulfilled, state => {
        state.data = { token: null, refreshToken: null, userInfo: null, isAuthenticated: false };
        state.loading = 'idle';
      });
  },
});

export default customerAuthSlice.reducer;
