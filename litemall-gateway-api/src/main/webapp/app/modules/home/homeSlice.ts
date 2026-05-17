import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { CategoryIndexResult } from 'app/shared/model/category/category.models';
import axios from 'axios';
import { HomeData } from '../../shared/model/home.models';

//export const getHomeData = createAsyncThunk<HomeData, void, { dispatch: AppDispatch; state: IRootState }>('home/data', async () => {

export const getHomeData = createAsyncThunk('home/data', async (_, thunkApi) => {
  const HomeUrl = BASE_URL_CONTEXT + '/catalog/all';
  const response = await axios.get(HomeUrl);
  return response.data.data;
});

export const getHomeAboutData = createAsyncThunk('home/about', async (_, thunkApi) => {
  const HomeUrl = BASE_URL_CONTEXT + '/home/about';
  const response = await axios.get(HomeUrl);
  return response.data.data;
});

export const getCatalogData = createAsyncThunk<CategoryIndexResult>('catalog/data', async (_, thunkApi) => {
  const CatalogUrl = BASE_URL_CONTEXT + '/catalog/all';
  const response = await axios.get(CatalogUrl);
  return response.data.data;
});

interface HomeState {
  loading: 'idle' | 'pending' | 'succeeded' | 'failed';
  errorMessage: string | null;
  errorNumber: number | null;
  homeData: HomeData;
  categoryIndexData: CategoryIndexResult;
}
const initialState = {
  loading: 'idle',
  errorMessage: null,
  homeData: {
    newGoodsList: [],
    couponList: [],
    channel: [],
    grouponList: [],
    banner: [],
    brandList: [],
    hotGoodsList: [],
    topicList: [],
    floorGoodsList: [],
  },
  categoryIndexData: {} as CategoryIndexResult,
  deleted: false,
  errorNumber: null,
} as HomeState;

const homeSlice = createSlice({
  name: 'homeState',
  initialState,
  reducers: {},
  extraReducers: builder => {
    builder
      .addCase(getHomeData.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.homeData = action.payload;
      })
      .addCase(getCatalogData.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.categoryIndexData.categoryList = action.payload.categoryList;
      });
  },
});

//export const {} = homeSlice.actions;
export default homeSlice.reducer;
