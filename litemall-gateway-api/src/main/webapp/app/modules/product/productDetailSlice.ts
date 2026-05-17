import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { ApiResult, BaseState } from 'app/config/types';
import { Comments, IAttribute, IGood, Info, Issue, ProductList, SpecificationList } from 'app/shared/model/product/product.model';
import axios from 'axios';

interface ProductDetailApiResult
  extends ApiResult<{
    specificationList: SpecificationList[];
    groupon: [];
    issue: Issue[];
    shareImage: string;
    comments: Comments;
    attribute: IAttribute[];
    productList: ProductList[];
    info: Info;
  }> {}

interface RelatedGoodsApiResult
  extends ApiResult<{
    total: number;
    pages: number;
    limit: number;
    page: number;
    list: IGood[];
  }> {}

export const getProductDetail = createAsyncThunk<ProductDetailApiResult['data'], number, { rejectValue: ApiResult<null> }>(
  'product/detail',
  async (goodsId: number, thunkApi) => {
    try {
      const ProductDetailUrl = BASE_URL_CONTEXT + '/goods/detail?id=' + goodsId;
      const response = await axios.get(ProductDetailUrl);
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

interface ProductDetailState
  extends BaseState<{
    specificationList: SpecificationList[];
    groupon: [];
    issue: Issue[];
    shareImage: string;
    comments: Comments;
    attribute: IAttribute[];
    productList: ProductList[];
    info: Info;
  }> {}

const initialState: ProductDetailState = {
  loading: 'idle',
  errorMessage: null,
  data: {
    specificationList: [],
    groupon: [],
    issue: [],
    shareImage: '',
    comments: {
      data: [],
      count: 0,
    },
    attribute: [],
    productList: [],
    info: {
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
  },
  errorNumber: null,
};

const categorySlice = createSlice({
  name: 'productDetailState',
  initialState,
  reducers: {},
  extraReducers: builder => {
    builder.addCase(getProductDetail.fulfilled, (state, action) => {
      state.loading = 'succeeded';
      state.data = action.payload;
    });
  },
});

export default categorySlice.reducer;
