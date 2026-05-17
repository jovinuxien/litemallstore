import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
//import { getUserInfo, getUserProfile } from 'app/config/axiosinstance';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { ApiResult, BaseState } from 'app/config/types';
import axios from 'axios';

interface InfoUserResult
  extends ApiResult<{
    gender: number;
    nickName: string;
    mobile: string;
    avatar: string;
  }> {}

/* export const getProfile = (): AppThunk => async (dispatch, getState) => {
  try {
    const response = await dispatch(getInfoUser()).unwrap();
    const userInfo = response.data?.userInfo;
    //const userInfo = getState().profile.data.user;
    const token = getState().auth.data.token;

    if (userInfo && token) {
      const profileUrl = BASE_URL_CONTEXT + '/auth/profile';
      const resultProfile = await authAxios.post(
        profileUrl,
        {
          nickname: userInfo.nickname,
          avatarUrl: userInfo.avatarUrl,
          gender: userInfo.gender,
        }
        //{ headers: { 'X-Litemall-Token': token } }
      );
      console.log(resultProfile.data);
      return resultProfile.data;
    }

    return console.log('Profile updated ');
  } catch (error) {
    console.error((error as Error).message || 'Logout request failed');
  }
}; */
export const getUserInfo = createAsyncThunk<InfoUserResult, void, { rejectValue: ApiResult<null> }>('/auth/info', async (_, thunkApi) => {
  try {
    const token = sessionStorage.getItem('token');

    if (!token) {
      return thunkApi.rejectWithValue({ errno: -1, errmsg: 'You need to login first', data: null });
    }

    const infoUrl = BASE_URL_CONTEXT + '/auth/info';
    const response = await axios.get<InfoUserResult>(infoUrl, { headers: { 'X-Litemall-Token': token } });
    console.log('The response is', response.data);

    if (response.data.data) {
      sessionStorage.setItem('userInfo', JSON.stringify(response.data.data));
    }
    return response.data;
  } catch (error) {
    return thunkApi.rejectWithValue({ errno: -1, errmsg: (error as Error).message || 'Info request failed', data: null });
  }
});

interface ProfileState
  extends BaseState<{
    gender: number;
    nickName: string;
    mobile: string;
    avatar: string;
  }> {}

const initialState: ProfileState = {
  loading: 'idle',
  errorMessage: null,
  data: JSON.parse(sessionStorage.getItem('userInfo') || '{}'),
  errorNumber: null,
};

const profileSlice = createSlice({
  name: 'profile',
  initialState,
  reducers: {},
  extraReducers: builder => {
    builder
      .addCase(getUserInfo.pending, state => {
        state.loading = 'pending';
      })
      .addCase(getUserInfo.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.data = action.payload.data;
      })
      .addCase(getUserInfo.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = action.payload?.errmsg || 'Failed to get user info';
      });
  },
});

/* export const { updatePassword, updateProfile } = profileSlice.actions; */

export default profileSlice.reducer;
