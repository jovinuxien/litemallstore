import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { ApiResult, BaseState, createApiClient } from '@litemall/shared';
import { authApi, RegisterBody } from 'app/shared/api';
import { t } from 'app/i18n';
import { describeError } from 'app/i18n/errors';

/**
 * Customer auth, split out of the former combined authSlice (no more
 * loginAdminThunk here). Talks to the gateway-api edge same-origin: relative
 * /auth/* (Phase 2 contract — {errno,errmsg,data}, Bearer JWT). The admin
 * realm now lives entirely in the gateway-admin SPA.
 */
const TOKEN_KEY = 'customerToken';
const REFRESH_TOKEN_KEY = 'customerRefreshToken';
const USER_INFO_KEY = 'customerUserInfo';
const api = createApiClient({ tokenKey: TOKEN_KEY });

export function clearCustomerSession(): void {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  sessionStorage.removeItem(USER_INFO_KEY);
}

interface Credentials {
  username: string;
  password: string;
}

interface CustomerInfo {
  nickName: string;
  avatarUrl: string;
  /** Wave 16: guest shadow account — the SPA offers "set a password" claims. */
  isGuest?: boolean;
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
    return thunkApi.rejectWithValue({ errno: -1, errmsg: t('errors:loginFailed'), data: null });
  }
});

/** Shared shape of every auth entry point's fulfilled payload. */
type AuthPayload = ApiResult<{ token: string; refreshToken: string; userInfo: CustomerInfo }>;

/** Raw same-origin POST to an /auth entry point, envelope in, envelope out. */
async function postAuth(path: string, body: unknown, thunkApi: { rejectWithValue: (v: ApiResult<null>) => unknown }, fallback: string) {
  try {
    const res = await fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const env = await res.json();
    if (env.errno !== 0) {
      return thunkApi.rejectWithValue({ errno: env.errno, errmsg: env.errmsg, data: null });
    }
    return env;
  } catch {
    return thunkApi.rejectWithValue({ errno: -1, errmsg: fallback, data: null });
  }
}

/**
 * Wave 16: guest checkout — a fresh password-less shadow account for this
 * email, logged straight in. errno 706 = the email has a real account; the
 * checkout gate switches to a sign-in prompt on it.
 */
export const guestCheckoutThunk = createAsyncThunk<AuthPayload, { email: string }, { rejectValue: ApiResult<null> }>(
  'customerAuth/guest',
  async (body, thunkApi) => postAuth('/auth/guest', body, thunkApi, 'Guest checkout failed') as Promise<AuthPayload>
);

/** Wave 16: Google Sign-In — the GIS credential verified at the edge. */
export const googleSignInThunk = createAsyncThunk<AuthPayload, { credential: string }, { rejectValue: ApiResult<null> }>(
  'customerAuth/google',
  async (body, thunkApi) => postAuth('/auth/google', body, thunkApi, 'Google sign-in failed') as Promise<AuthPayload>
);

export const registerCustomerThunk = createAsyncThunk<
  ApiResult<{ token: string; refreshToken: string; userInfo: CustomerInfo }>,
  RegisterBody,
  { rejectValue: ApiResult<null> }
>('customerAuth/register', async (body, thunkApi) => {
  try {
    const env = await authApi.register(body);
    if (env.errno !== 0) {
      return thunkApi.rejectWithValue({ errno: env.errno, errmsg: env.errmsg, data: null });
    }
    return env as ApiResult<{ token: string; refreshToken: string; userInfo: CustomerInfo }>;
  } catch (e) {
    return thunkApi.rejectWithValue({ errno: -1, errmsg: t('errors:registrationFailed'), data: null });
  }
});

export const logoutCustomerThunk = createAsyncThunk('customerAuth/logout', async () => {
  const refreshToken = sessionStorage.getItem(REFRESH_TOKEN_KEY);
  try {
    await api.post('/auth/logout', { refreshToken });
  } catch {
    // Best-effort revoke: logout must still succeed locally when the edge
    // call fails, so the fulfilled reducer always resets the auth state.
  } finally {
    clearCustomerSession();
  }
});

/**
 * Rehydrate auth from sessionStorage so a page reload does not log the
 * customer out: the JWT survives the reload but redux used to reset to
 * isAuthenticated=false, bouncing protected routes to /login.
 */
function restoreFromStorage(): CustomerAuthData {
  const token = sessionStorage.getItem(TOKEN_KEY);
  const refreshToken = sessionStorage.getItem(REFRESH_TOKEN_KEY);
  let userInfo: CustomerInfo | null = null;
  try {
    const raw = sessionStorage.getItem(USER_INFO_KEY);
    if (raw) userInfo = JSON.parse(raw);
  } catch {
    userInfo = null;
  }
  return { token, refreshToken, userInfo, isAuthenticated: !!token };
}

const loggedOutData: CustomerAuthData = { token: null, refreshToken: null, userInfo: null, isAuthenticated: false };

const initialState: BaseState<CustomerAuthData> = {
  loading: 'idle',
  errorMessage: null,
  errorNumber: null,
  data: restoreFromStorage(),
};

const customerAuthSlice = createSlice({
  name: 'customerAuth',
  initialState,
  reducers: {
    // For in-app callers that detect an expired/invalid token (the axios 401
    // interceptor clears storage and hard-redirects instead, to avoid a
    // circular store import).
    sessionExpired(state) {
      clearCustomerSession();
      state.data = loggedOutData;
      state.loading = 'idle';
    },
  },
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
        if (d.refreshToken) sessionStorage.setItem(REFRESH_TOKEN_KEY, d.refreshToken);
        if (d.userInfo) sessionStorage.setItem(USER_INFO_KEY, JSON.stringify(d.userInfo));
      })
      .addCase(loginCustomerThunk.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = describeError(action.payload?.errno, action.payload?.errmsg ?? t('errors:loginFailed'));
        state.errorNumber = action.payload?.errno ?? -1;
      })
      .addCase(registerCustomerThunk.pending, state => {
        state.loading = 'pending';
        state.errorMessage = null;
      })
      .addCase(registerCustomerThunk.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        const d = action.payload.data;
        state.data = { ...d, isAuthenticated: true };
        if (d.token) sessionStorage.setItem(TOKEN_KEY, d.token);
        if (d.refreshToken) sessionStorage.setItem(REFRESH_TOKEN_KEY, d.refreshToken);
        if (d.userInfo) sessionStorage.setItem(USER_INFO_KEY, JSON.stringify(d.userInfo));
      })
      .addCase(registerCustomerThunk.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = describeError(action.payload?.errno, action.payload?.errmsg ?? t('errors:registrationFailed'));
        state.errorNumber = action.payload?.errno ?? -1;
      })
      .addCase(logoutCustomerThunk.fulfilled, state => {
        state.data = loggedOutData;
        state.loading = 'idle';
      })
      // Wave 16: guest + Google entry points persist the session exactly like
      // login/register — one storage contract for every way in.
      .addCase(guestCheckoutThunk.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        const d = action.payload.data;
        state.data = { ...d, isAuthenticated: true };
        if (d.token) sessionStorage.setItem(TOKEN_KEY, d.token);
        if (d.refreshToken) sessionStorage.setItem(REFRESH_TOKEN_KEY, d.refreshToken);
        if (d.userInfo) sessionStorage.setItem(USER_INFO_KEY, JSON.stringify(d.userInfo));
      })
      .addCase(guestCheckoutThunk.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = describeError(action.payload?.errno, action.payload?.errmsg ?? t('errors:guestCheckoutFailed'));
        state.errorNumber = action.payload?.errno ?? -1;
      })
      .addCase(googleSignInThunk.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        const d = action.payload.data;
        state.data = { ...d, isAuthenticated: true };
        if (d.token) sessionStorage.setItem(TOKEN_KEY, d.token);
        if (d.refreshToken) sessionStorage.setItem(REFRESH_TOKEN_KEY, d.refreshToken);
        if (d.userInfo) sessionStorage.setItem(USER_INFO_KEY, JSON.stringify(d.userInfo));
      })
      .addCase(googleSignInThunk.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = describeError(action.payload?.errno, action.payload?.errmsg ?? t('errors:googleFailed'));
        state.errorNumber = action.payload?.errno ?? -1;
      });
  },
});

export const { sessionExpired } = customerAuthSlice.actions;
export default customerAuthSlice.reducer;
