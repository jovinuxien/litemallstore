import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { CategoryIndexResult, L1CategoryList } from 'app/shared/model/category/category.models';
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



interface HomeState {
  loading: 'idle' | 'pending' | 'succeeded' | 'failed';
  errorMessage: string | null;
  errorNumber: number | null;
  homeData: HomeData;
  categoryIndexData: CategoryIndexResult;
  l1CategoryData: L1CategoryList;
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
  l1CategoryData: {} as L1CategoryList,
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
     /*  .addCase(getFirstCategories.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        //state.categoryIndexData.categoryList = action.payload.categoryList;
        state.l1CategoryData  = action.payload
      }); */
  },
});

//export const {} = homeSlice.actions;
export default homeSlice.reducer;
