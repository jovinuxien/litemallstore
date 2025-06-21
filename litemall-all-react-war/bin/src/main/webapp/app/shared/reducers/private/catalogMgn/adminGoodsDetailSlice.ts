import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { ADMIN_URL_CONTEXT } from 'app/config/api';
import { ApiResult, BaseState } from 'app/config/types';
import { IAttribute, IGoodsDetail, ProductList, SpecificationList } from 'app/shared/model/product/product.model';
import axios from 'axios';

interface AdminGoodsDetailResult
  extends ApiResult<{
    goods: IGoodsDetail;
    specificationList: SpecificationList[];
    products: ProductList[];
    attributes: IAttribute[];
    categoryIds: number[];
  }> {}

export const getAdminGoodsDetailThunk = createAsyncThunk<AdminGoodsDetailResult['data'], number, { rejectValue: ApiResult<null> }>(
  'admingoodsdetail/list',
  async (goodsId, thunkApi) => {
    const adminToken = sessionStorage.getItem('adminToken');
    if (adminToken !== null) {
      try {
        const goodDetailsUrl = `${ADMIN_URL_CONTEXT}/goods/detail?id=${goodsId}`;
        const response = await axios.get(goodDetailsUrl, { headers: { 'X-Litemall-Admin-Token': adminToken } });
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
    } else {
      return thunkApi.rejectWithValue({
        errno: -1,
        errmsg: 'You need to login first',
        data: null,
      });
    }
  }
);

interface AdminGoodsDetailState
  extends BaseState<{
    goods: IGoodsDetail;
    specificationList: SpecificationList[];
    products: ProductList[];
    attributes: IAttribute[];
    categoryIds: number[];
  }> {}

const initialState: AdminGoodsDetailState = {
  loading: 'idle',
  errorMessage: '',
  data: {
    attributes: [],
    categoryIds: [],
    goods: {
      id: 0,
      goodsSn: 0,
      name: '',
      categoryId: 0,
      brandId: 0,
      gallery: [],
      keywords: '',
      brief: '', //'Crispy and milky, sweet and sour aftertaste';
      isOnSale: false,
      sortOrder: 0,
      picUrl: '', //'http://yanxuan.nosdn.127.net/767b370d07f3973500db54900bcbd2a7.png';
      shareUrl: '',
      isNew: false,
      isHot: false,
      unit: '',
      counterPrice: 0,
      retailPrice: 0,
      addTime: new Date(),
      updateTime: new Date(),
      deleted: false,
      detail: '',
    },
    products: [],
    specificationList: [],
  },
  errorNumber: null,
};

const adminGoodsDetailSlice = createSlice({
  name: 'adminGoodsDetail',
  initialState,
  reducers: {},
  extraReducers: builder => {
    builder
      .addCase(getAdminGoodsDetailThunk.pending, (state, action) => {
        state.loading = 'pending';
      })
      .addCase(getAdminGoodsDetailThunk.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.data = action.payload;
      })
      .addCase(getAdminGoodsDetailThunk.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = action.error.message;
      });
  },
});

export default adminGoodsDetailSlice.reducer;
