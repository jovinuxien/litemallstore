import { createAsyncThunk, createSlice, PayloadAction } from '@reduxjs/toolkit';
import axios from 'axios';

import { AUTHORITIES } from 'app/config/constants';

// Single source of truth for the admin-edge session (admin OR affiliate).
//
// Two principal types share this edge (Wave 5): admins log in at
// `POST /auth/login` (litemall_admin), affiliates at `POST /auth/affiliate/login`
// (litemall_user promoters). Both return a self-signed edge JWT kept in redux
// and mirrored in localStorage so a reload restores the session; the ROLE is
// persisted alongside it so `authorities` survives the reload too. Every `/srv`
// call carries `Authorization: Bearer <jwt>` (RTK Query prepareHeaders +
// config/axios-interceptor); the edge enforces ROLE_ADMIN on
// `/srv/private/admin/**` and ROLE_AFFILIATE on `/srv/private/affiliate/**` —
// the SPA role gating on top of this is UX only, never the boundary.

export const ADMIN_TOKEN_KEY = 'admin-jwt';
export const ADMIN_REFRESH_KEY = 'admin-refresh';
export const ADMIN_ROLE_KEY = 'admin-role';

export interface AdminInfo {
  nickName?: string;
  avatarUrl?: string;
}

interface LoginPayload {
  token: string;
  refreshToken: string;
  adminInfo: AdminInfo;
  role: string;
}

/** Reads the persisted admin JWT (used by the axios interceptor outside React). */
export const getAdminToken = (): string | null => {
  try {
    return localStorage.getItem(ADMIN_TOKEN_KEY);
  } catch {
    return null;
  }
};

/** Persisted role for the current session; defaults to ADMIN for pre-Wave-5 sessions. */
export const getPersistedRole = (): string => {
  try {
    const role = localStorage.getItem(ADMIN_ROLE_KEY);
    return role === AUTHORITIES.AFFILIATE ? AUTHORITIES.AFFILIATE : AUTHORITIES.ADMIN;
  } catch {
    return AUTHORITIES.ADMIN;
  }
};

const loginThunk = (typePrefix: string, url: string, role: string, infoKey: 'adminInfo' | 'affiliateInfo') =>
  createAsyncThunk<LoginPayload, { username: string; password: string }, { rejectValue: string }>(
    typePrefix,
    async (credentials, thunkApi) => {
      try {
        // `/auth/**` is permitted at the edge and is NOT under the '/srv' prefix.
        const response = await axios.post(url, credentials);
        const body = response.data;
        if (!body || body.errno !== 0 || !body.data?.token) {
          return thunkApi.rejectWithValue(body?.errmsg || 'Login failed');
        }
        const data = body.data;
        return {
          token: data.token,
          refreshToken: data.refreshToken,
          adminInfo: data[infoKey] || {},
          role,
        };
      } catch (error) {
        const msg = (error as { response?: { data?: { errmsg?: string } } })?.response?.data?.errmsg;
        return thunkApi.rejectWithValue(msg || 'Login failed');
      }
    }
  );

export const loginAdmin = loginThunk('adminAuth/login', '/auth/login', AUTHORITIES.ADMIN, 'adminInfo');
// Affiliate login: non-promoters come back errno 403 "not an affiliate" — the
// errmsg is surfaced as-is on the login form.
export const loginAffiliate = loginThunk('adminAuth/loginAffiliate', '/auth/affiliate/login', AUTHORITIES.AFFILIATE, 'affiliateInfo');

export const logoutAdmin = createAsyncThunk('adminAuth/logout', async (_, thunkApi) => {
  const refreshToken = (() => {
    try {
      return localStorage.getItem(ADMIN_REFRESH_KEY);
    } catch {
      return null;
    }
  })();
  if (refreshToken) {
    // Revoke against the realm the session was minted by.
    const url = getPersistedRole() === AUTHORITIES.AFFILIATE ? '/auth/affiliate/logout' : '/auth/logout';
    try {
      await axios.post(url, { refreshToken });
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
  authorities: initialToken ? [getPersistedRole()] : [],
  isAuthenticated: !!initialToken,
  bootstrapped: true,
  loading: false,
  errorMessage: null,
};

const persist = (token: string, refreshToken: string, role: string) => {
  try {
    localStorage.setItem(ADMIN_TOKEN_KEY, token);
    if (refreshToken) localStorage.setItem(ADMIN_REFRESH_KEY, refreshToken);
    localStorage.setItem(ADMIN_ROLE_KEY, role);
  } catch {
    /* storage unavailable — session lives in redux only */
  }
};

const clearPersisted = () => {
  try {
    localStorage.removeItem(ADMIN_TOKEN_KEY);
    localStorage.removeItem(ADMIN_REFRESH_KEY);
    localStorage.removeItem(ADMIN_ROLE_KEY);
  } catch {
    /* noop */
  }
};

const applyLogin = (state: AdminAuthState, action: PayloadAction<LoginPayload>) => {
  state.loading = false;
  state.token = action.payload.token;
  state.refreshToken = action.payload.refreshToken;
  state.adminInfo = action.payload.adminInfo;
  state.authorities = [action.payload.role];
  state.isAuthenticated = true;
  persist(action.payload.token, action.payload.refreshToken, action.payload.role);
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
      .addCase(loginAdmin.fulfilled, applyLogin)
      .addCase(loginAffiliate.fulfilled, applyLogin)
      .addCase(logoutAdmin.fulfilled, state => {
        clearPersisted();
        state.token = null;
        state.refreshToken = null;
        state.adminInfo = null;
        state.authorities = [];
        state.isAuthenticated = false;
      })
      .addMatcher(
        action => action.type === loginAdmin.pending.type || action.type === loginAffiliate.pending.type,
        state => {
          state.loading = true;
          state.errorMessage = null;
        }
      )
      .addMatcher(
        (action): action is ReturnType<typeof loginAdmin.rejected> =>
          action.type === loginAdmin.rejected.type || action.type === loginAffiliate.rejected.type,
        (state, action) => {
          state.loading = false;
          state.isAuthenticated = false;
          state.errorMessage = (action.payload as string) || action.error?.message || 'Login failed';
        }
      );
  },
});

export const { clearAdminAuth } = adminAuthSlice.actions;
export default adminAuthSlice.reducer;
