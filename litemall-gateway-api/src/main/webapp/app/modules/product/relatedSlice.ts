import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { ApiResult, BaseState } from 'app/config/types';
import { IGood } from 'app/shared/model/product/product.model';

interface RelatedGoodsApiResult
  extends ApiResult<{
    total: number;
    pages: number;
    limit: number;
    page: number;
    list: IGood[];
  }> {}

export const getRelatedGoods = createAsyncThunk<RelatedGoodsApiResult['data'], string, { rejectValue: ApiResult<null> }>(
  'goods/related',
  async (goodsId: string, thunkApi) => {
    try {
      const relatedGoodsUrl = BASE_URL_CONTEXT + '/goods/related?id=' + goodsId;
      const response = await baseAxios.get(relatedGoodsUrl);
      if (response.data.errno !== 0) {
        return thunkApi.rejectWithValue({
          errno: response.data.errno,
          errmsg: response.data.errmsg,
          data: null,
        });
      }
      return response.data.data;
    } catch (error) {
      return thunkApi.rejectWithValue({
        errno: 500,
        errmsg: error.message,
        data: null,
      });
    }
  }
);

interface RelatedGoodsState
  extends BaseState<{
    total: number;
    pages: number;
    limit: number;
    page: number;
    list: IGood[];
  }> {}

const initialState: RelatedGoodsState = {
  loading: 'idle',
  errorMessage: null,
  data: {
    total: 0,
    pages: 0,
    limit: 0,
    page: 0,
    list: [],
  },
  errorNumber: null,
};

const relatedGoodsSlice = createSlice({
  name: 'productRelatedState',
  initialState,
  reducers: {},
  extraReducers: builder => {
    builder.addCase(getRelatedGoods.fulfilled, (state, action) => {
      state.loading = 'succeeded';
      state.data = {
        ...action.payload,
        list: action.payload.list as IGood[],
      };
    });
  },
});

export default relatedGoodsSlice.reducer;
