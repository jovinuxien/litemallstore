import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { ApiResult, BaseState } from 'app/config/types';
import { CategoryData } from 'app/shared/model/category/category.models';
import { IGood } from 'app/shared/model/product/product.model';
import axios from 'axios';

interface ProductListApiResult
  extends ApiResult<{
    total: number | null;
    pages: number | null;
    limit: number | null;
    page: number | null;
    list: IGood[];
    filterCategoryList: CategoryData[];
  }> {}

export const getProductList = createAsyncThunk<ProductListApiResult['data'], void, { rejectValue: ApiResult<null> }>('product/list', async (_, thunkApi) => {
  try {
    const CatalogUrl = BASE_URL_CONTEXT + '/goods/list';
    const response = await axios.get(CatalogUrl);
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
});

/* export const goodsByCategoryId = createAsyncThunk<GoodCategoryResult, number>('goods/goodsByCategoryId', async (categoryId: number, _) => {
  const goodsByCategoryIdUrl = BASE_URL_CONTEXT + '/catalog/goods?id=' + categoryId;
  const response = await axios.get(goodsByCategoryIdUrl);
  console.log(response);
  return response.data.data;
}); */

interface ProductState
  extends BaseState<{
    total: number | null;
    pages: number | null;
    limit: number | null;
    page: number | null;
    list: IGood[];
    filterCategoryList: CategoryData[];
  }> {}

const initialState: ProductState = {
  loading: 'idle',
  errorMessage: null,
  data: {
    total: 0,
    pages: 0,
    limit: 0,
    page: 0,
    list: [],
    filterCategoryList: [],
  },
  errorNumber: null,
};

const productSlice = createSlice({
  name: 'categoryState',
  initialState,
  reducers: {},
  extraReducers: builder => {
    builder.addCase(getProductList.fulfilled, (state, action) => {
      state.loading = 'succeeded';
      state.data = action.payload;
    });
  },
});

//export const {} = homeSlice.actions;
export default productSlice.reducer;
