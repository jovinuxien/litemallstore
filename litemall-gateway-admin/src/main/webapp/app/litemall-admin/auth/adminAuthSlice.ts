import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { ApiResult, BaseState, createApiClient } from '@litemall/shared';

/**
 * Admin auth, split out of the former combined authSlice (no more
 * loginUserThunk here). Talks to the gateway-admin edge same-origin:
 * relative /auth/* (Phase 3c contract — {errno,errmsg,data}, Bearer JWT,
 * adminInfo). The customer realm now lives entirely in the gateway-api SPA.
 * This is the admin store's auth slice — fully independent of the customer
 * store.
 */
const TOKEN_KEY = 'adminToken';
const api = createApiClient({ tokenKey: TOKEN_KEY });

interface Credentials {
  username: string;
  password: string;
}

interface AdminInfo {
  nickName: string;
  avatarUrl: string;
}

export interface AdminAuthData {
  token: string | null;
  refreshToken: string | null;
  adminInfo: AdminInfo | null;
  isAuthenticated: boolean;
}

export const loginAdminThunk = createAsyncThunk<
  ApiResult<{ token: string; refreshToken: string; adminInfo: AdminInfo }>,
  Credentials,
  { rejectValue: ApiResult<null> }
>('adminAuth/login', async ({ username, password }, thunkApi) => {
  try {
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
    return thunkApi.rejectWithValue({ errno: -1, errmsg: 'Admin login failed', data: null });
  }
});

export const logoutAdminThunk = createAsyncThunk('adminAuth/logout', async () => {
  const refreshToken = sessionStorage.getItem('adminRefreshToken');
  await api.post('/auth/logout', { refreshToken });
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem('adminRefreshToken');
});

const initialState: BaseState<AdminAuthData> = {
  loading: 'idle',
  errorMessage: null,
  errorNumber: null,
  data: { token: null, refreshToken: null, adminInfo: null, isAuthenticated: false },
};

const adminAuthSlice = createSlice({
  name: 'adminAuth',
  initialState,
  reducers: {},
  extraReducers: builder => {
    builder
      .addCase(loginAdminThunk.pending, state => {
        state.loading = 'pending';
        state.errorMessage = null;
      })
      .addCase(loginAdminThunk.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        const d = action.payload.data;
        state.data = { ...d, isAuthenticated: true };
        if (d.token) sessionStorage.setItem(TOKEN_KEY, d.token);
        if (d.refreshToken) sessionStorage.setItem('adminRefreshToken', d.refreshToken);
      })
      .addCase(loginAdminThunk.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = action.payload?.errmsg ?? 'Admin login failed';
        state.errorNumber = action.payload?.errno ?? -1;
      })
      .addCase(logoutAdminThunk.fulfilled, state => {
        state.data = { token: null, refreshToken: null, adminInfo: null, isAuthenticated: false };
        state.loading = 'idle';
      });
  },
});

export default adminAuthSlice.reducer;
