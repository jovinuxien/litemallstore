import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { ADMIN_URL_CONTEXT, BASE_URL_CONTEXT } from 'app/config/api';
import { authAxios, baseAxios } from 'app/config/axiosinstance';
import { AppThunk } from 'app/config/store';
import { ApiResult, BaseState } from 'app/config/types';
import axios from 'axios';
import { IAdminInfo, IUserInfo } from '../model/user.models';

interface UserInfoCall {
  nickname: string;
  avatarUrl: string;
}

interface AdminInfoCall {
  adminInfo: IAdminInfo;
}

interface Credentials {
  username: string;
  password: string;
}

export interface AdminAuthResult
  extends ApiResult<{
    token: string | null;
    adminInfo: AdminInfoCall;
  }> {}
export interface AuthResult
  extends ApiResult<{
    token: string | null;
    user: UserInfoCall;
  }> {}

export const authenticate =
  (credential: { username: string; password: string }): AppThunk =>
  async (dispatch, getState) => {
    const userToken = sessionStorage.getItem('token');
    const adminToken = sessionStorage.getItem('adminToken');

    // Check if user is already authenticated
    if (userToken) {
      console.log('User already authenticated');
      return { data: { token: userToken }, errno: 0, errmsg: 'Already authenticated' };
    }

    // Check if admin is already authenticated
    if (adminToken) {
      console.log('Admin already authenticated');
      return { data: { token: adminToken, isAdmin: true }, errno: 0, errmsg: 'Already authenticated as admin' };
    }

    let result, adminResult;

    try {
      result = await dispatch(loginUserThunk(credential)).unwrap();
      if (result.data?.token) {
        sessionStorage.setItem('token', result.data.token);
        console.log('User token ', result);
        return result;
      }
    } catch (error) {
      console.error('Login Error, and the reason is:', error);
    }

    try {
      adminResult = await dispatch(loginAdminThunk(credential)).unwrap();
      if (adminResult.data?.token) {
        sessionStorage.setItem('adminToken', adminResult.data.token);
        console.log('Admin token ', adminResult);
        return adminResult;
      }
    } catch (error) {
      console.error('Admin Login Error, and the reason is:', error);
    }

    // If both login attempts fail
    return { errno: -1, errmsg: 'Authentication failed', data: null };
  };

export const loginUserThunk = createAsyncThunk<AuthResult, Credentials, { rejectValue: ApiResult<null> }>(
  'auth/login',
  async ({ username, password }: Credentials, thunkApi) => {
    try {
      const authURL = BASE_URL_CONTEXT + '/auth/login';
      const response = await baseAxios.post<AuthResult>(authURL, {
        username,
        password,
      });

      return response.data;
    } catch (error) {
      return thunkApi.rejectWithValue({
        errno: -1,
        errmsg: error.response?.data?.message || 'Login failed',
        data: null,
      });
    }
  }
);

export const loginAdminThunk = createAsyncThunk<AdminAuthResult, Credentials, { rejectValue: ApiResult<null> }>(
  'admin/auth',
  async ({ username, password }: Credentials, thunkApi) => {
    try {
      const authURL = ADMIN_URL_CONTEXT + '/auth/login';
      const response = await axios.post<AdminAuthResult>(authURL, {
        username,
        password,
      });

      return response.data;
    } catch (error) {
      return thunkApi.rejectWithValue({
        errno: -1,
        errmsg: error.response?.data?.message || 'Admin  Login failed',
        data: null,
      });
    }
  }
);

export const logoutThunk = createAsyncThunk('auth/logout', async (_, { rejectWithValue, dispatch, getState }) => {
  try {
    const token = sessionStorage.getItem('token');
    if (!token) {
      return rejectWithValue('no token found in session storage');
    }
    const logoutUrl = BASE_URL_CONTEXT + '/auth/logout';

    const response = await authAxios.post(
      logoutUrl,
      {},
      {
        headers: {
          'X-Litemall-Token': token,
        },
      }
    );
    sessionStorage.removeItem('token');
    return response;
  } catch (error) {
    return rejectWithValue((error as Error).message || 'Logout request failed');
  }
});

export const logoutAdminThunk = createAsyncThunk('admin/logout', async (_, { rejectWithValue, dispatch, getState }) => {
  try {
    const token = sessionStorage.getItem('adminToken');
    if (!token) {
      return rejectWithValue('no token found in session storage');
    }
    const logoutUrl = ADMIN_URL_CONTEXT + '/auth/logout';

    const response = await authAxios.post(
      logoutUrl,
      {},
      {
        headers: {
          'X-Litemall-Admin-Token': token,
        },
      }
    );
    sessionStorage.removeItem('adminToken');
    return response;
  } catch (error) {
    return rejectWithValue((error as Error).message || 'Logout request failed');
  }
});

export interface AuthState
  extends BaseState<{
    token: string;
    adminToken: string;
    user: IUserInfo | IAdminInfo | null;
    error: string;
    isAuthenticated: boolean;
    isAuthenticatedAdmin: boolean;
  }> {}

const initialState: AuthState = {
  loading: 'idle',
  errorMessage: null,
  data: {
    isAuthenticated: !!sessionStorage.getItem('token'),
    isAuthenticatedAdmin: !!sessionStorage.getItem('adminToken'),
    token: sessionStorage.getItem('token') || null,
    adminToken: sessionStorage.getItem('adminToken') || null,
    user: null,
    error: '',
  },
  errorNumber: null,
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    resetError: state => {
      state = null;
    },
    loginSuccess: (state, action) => {
      state.data.token = action.payload.token;
      state.data.isAuthenticated = true;
    },
  },
  extraReducers: builder => {
    builder
      // Login
      .addCase(loginUserThunk.pending, (state, action) => {
        state.loading = 'pending';
        state.data.token = null;
      })
      .addCase(loginUserThunk.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        if (action.payload?.data?.token) {
          state.data.user = action.payload.data.user as IUserInfo;
        } else {
          state.data.user = null;
        }
        state.data.isAuthenticated = !!action.payload?.data?.token;
      })
      .addCase(loginAdminThunk.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.data.user = action.payload.data.adminInfo as unknown as IAdminInfo;
        state.data.isAuthenticatedAdmin = !!action.payload?.data?.token;
      })
      .addCase(logoutThunk.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.data.user = {
          nickname: '',
          avatarUrl: '',
        };
        state.data.isAuthenticated = false;
      });
  },
});

export const { resetError } = authSlice.actions;

export default authSlice.reducer;
