import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import axios from 'axios';

import { AUTHORITIES } from 'app/config/constants';

// Single source of truth for the admin session.
//
// The admin logs in at the edge (`POST /auth/login`, see gateway AuthController),
// which returns a self-signed admin JWT. We keep that JWT in redux and mirror it
// in localStorage so a reload restores the session. Every `/srv` call then
// carries `Authorization: Bearer <jwt>` (RTK Query prepareHeaders +
// config/axios-interceptor); the edge validates it for `/srv/private/admin/**`
// and `/srv/order/admin/**` and relays a trusted identity downstream via
// MachineTokenRelayFilter. The legacy session-stored token + per-request admin
// header + hardcoded host scheme is fully removed.

export const ADMIN_TOKEN_KEY = 'admin-jwt';
export const ADMIN_REFRESH_KEY = 'admin-refresh';

export interface AdminInfo {
  nickName?: string;
  avatarUrl?: string;
}

interface LoginPayload {
  token: string;
  refreshToken: string;
  adminInfo: AdminInfo;
}

/** Reads the persisted admin JWT (used by the axios interceptor outside React). */
export const getAdminToken = (): string | null => {
  try {
    return localStorage.getItem(ADMIN_TOKEN_KEY);
  } catch {
    return null;
  }
};

export const loginAdmin = createAsyncThunk<LoginPayload, { username: string; password: string }, { rejectValue: string }>(
  'adminAuth/login',
  async (credentials, thunkApi) => {
    try {
      // `/auth/**` is permitted at the edge and is NOT under the '/srv' prefix.
      const response = await axios.post('/auth/login', credentials);
      const body = response.data;
      if (!body || body.errno !== 0 || !body.data?.token) {
        return thunkApi.rejectWithValue(body?.errmsg || 'Login failed');
      }
      const data = body.data;
      return {
        token: data.token,
        refreshToken: data.refreshToken,
        adminInfo: data.adminInfo || {},
      };
    } catch (error) {
      const msg = (error as { response?: { data?: { errmsg?: string } } })?.response?.data?.errmsg;
      return thunkApi.rejectWithValue(msg || 'Login failed');
    }
  }
);

export const logoutAdmin = createAsyncThunk('adminAuth/logout', async (_, thunkApi) => {
  const refreshToken = (() => {
    try {
      return localStorage.getItem(ADMIN_REFRESH_KEY);
    } catch {
      return null;
    }
  })();
  if (refreshToken) {
    try {
      await axios.post('/auth/logout', { refreshToken });
    } catch {
      // best-effort; we clear local state regardless
    }
  }
  return true;
});

interface AdminAuthState {
  token: string | null;
  refreshToken: string | null;
  adminInfo: AdminInfo | null;
  authorities: string[];
  isAuthenticated: boolean;
  // Synchronous bootstrap from localStorage means the session is known at first
  // render — no async account fetch to wait on (mirrors the old
  // `sessionHasBeenFetched` gate for private-route).
  bootstrapped: boolean;
  loading: boolean;
  errorMessage: string | null;
}

const initialToken = getAdminToken();

const initialState: AdminAuthState = {
  token: initialToken,
  refreshToken: (() => {
    try {
      return localStorage.getItem(ADMIN_REFRESH_KEY);
    } catch {
      return null;
    }
  })(),
  adminInfo: null,
  authorities: initialToken ? [AUTHORITIES.ADMIN] : [],
  isAuthenticated: !!initialToken,
  bootstrapped: true,
  loading: false,
  errorMessage: null,
};

const persist = (token: string, refreshToken: string) => {
  try {
    localStorage.setItem(ADMIN_TOKEN_KEY, token);
    if (refreshToken) localStorage.setItem(ADMIN_REFRESH_KEY, refreshToken);
  } catch {
    /* storage unavailable — session lives in redux only */
  }
};

const clearPersisted = () => {
  try {
    localStorage.removeItem(ADMIN_TOKEN_KEY);
    localStorage.removeItem(ADMIN_REFRESH_KEY);
  } catch {
    /* noop */
  }
};

const adminAuthSlice = createSlice({
  name: 'adminAuth',
  initialState,
  reducers: {
    // Dispatched by the axios interceptor on a 401/403 from any /srv call.
    clearAdminAuth: state => {
      clearPersisted();
      state.token = null;
      state.refreshToken = null;
      state.adminInfo = null;
      state.authorities = [];
      state.isAuthenticated = false;
      state.errorMessage = null;
    },
  },
  extraReducers: builder => {
    builder
      .addCase(loginAdmin.pending, state => {
        state.loading = true;
        state.errorMessage = null;
      })
      .addCase(loginAdmin.fulfilled, (state, action) => {
        state.loading = false;
        state.token = action.payload.token;
        state.refreshToken = action.payload.refreshToken;
        state.adminInfo = action.payload.adminInfo;
        state.authorities = [AUTHORITIES.ADMIN];
        state.isAuthenticated = true;
        persist(action.payload.token, action.payload.refreshToken);
      })
      .addCase(loginAdmin.rejected, (state, action) => {
        state.loading = false;
        state.isAuthenticated = false;
        state.errorMessage = action.payload || action.error.message || 'Login failed';
      })
      .addCase(logoutAdmin.fulfilled, state => {
        clearPersisted();
        state.token = null;
        state.refreshToken = null;
        state.adminInfo = null;
        state.authorities = [];
        state.isAuthenticated = false;
      });
  },
});

export const { clearAdminAuth } = adminAuthSlice.actions;
export default adminAuthSlice.reducer;
