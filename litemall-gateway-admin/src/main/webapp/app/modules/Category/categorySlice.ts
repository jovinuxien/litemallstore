import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { ApiResult, BaseState } from 'app/config/types';
import { CategoryData } from 'app/shared/model/category/category.models';
import { ReducedGood } from 'app/shared/model/product/product.model';
import axios from 'axios';

interface AllCategoryList {
  idCategory: CategoryData[];
}

interface CategoryIndexApiResult
  extends ApiResult<{
    currentCategory: CategoryData | null;
    categoryList: CategoryData[];
    subCategoryList: CategoryData[];
  }> {}
export interface CategoryCurrentApiResult
  extends ApiResult<{
    currentCategory: CategoryData | null;
    currentSubCategory: CategoryData[];
  }> {}

interface AllCategoryApiResult
  extends ApiResult<{
    allList: AllCategoryList;
    currentCategory: CategoryData | null;
    currentSubCategory: CategoryData[];
  }> {}

interface FirstCategoryListApiResult extends 
ApiResult<{
    l1CatList: CategoryData[]
}> {}

interface SecondCategoryApiResult
  extends ApiResult<{
    currentCategory: CategoryData | null;
    secondCategories: CategoryData[];
  }> {}

export interface GoodCategoryResult
  extends ApiResult<{
    currentCategory: CategoryData | null;
    goodsCategory: ReducedGood[];
  }> {}


 
/* export const getCatalogIndexData = createAsyncThunk<CategoryIndexApiResult['data'], void, { rejectValue: ApiResult<null> }>(
  'indexCategory/data',
  async (_, thunkApi) => {
    try {
      const CatalogUrl = BASE_URL_CONTEXT + '/catalog/index';
      const response = await baseAxios.get(CatalogUrl);
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
); */

export const getCurrentCatalogData = createAsyncThunk<CategoryCurrentApiResult['data'], number, { rejectValue: ApiResult<null> }>(
  'currentCategory/data',
  async (currentId: number, thunkApi) => {
    try {
      const currentCatalogUrl = BASE_URL_CONTEXT + '/catalog/current?id=' + currentId;
      const response = await baseAxios.get(currentCatalogUrl);
      if (response.data.errno !== 0) {
        return thunkApi.rejectWithValue({
          errno: response.data.errno,
          errmsg: response.data.errmsg,
          data: null,
        });
      }
      return response.data.data ;
    } catch (error) {
      return thunkApi.rejectWithValue({
        errno: 500,
        errmsg: error.message,
        data: null,
      });
    }
  }
);

export const getGoodsOfDefaultFirstSubCategory = createAsyncThunk<GoodCategoryResult, number, { rejectValue: ApiResult<null> }>(
  'getGoodsOfDefaultFirstSubCategory/data',
  async (currentId: number, thunkApi) => {
    try {
      const currentCatalogData = await thunkApi.dispatch(getCurrentCatalogData(currentId));
      const currentCategory = currentCatalogData.payload as CategoryCurrentApiResult['data'];
      const defaultSubCategoryId = currentCategory.currentSubCategory[0].id;
      if (!defaultSubCategoryId) {
        return thunkApi.rejectWithValue({
          errno: 500,
          errmsg: 'Something wrong with defaultSubCategoryId',
          data: null,
        });
      }
      const goodsOfTheDefaultSubCategoryData = await thunkApi.dispatch(goodsBySubCategoryId(defaultSubCategoryId));

      if (!goodsOfTheDefaultSubCategoryData.payload) {
        return thunkApi.rejectWithValue({
          errno: 500,
          errmsg: 'Something wrong with goodsOfTheDefaultSubCategoryData',
          data: null,
        });
      }
      return goodsOfTheDefaultSubCategoryData.payload;
    } catch (error) {
      return thunkApi.rejectWithValue({
        errno: 500,
        errmsg: error.message,
        data: null,
      });
    }
  }
);

  export const getFirstCategories = createAsyncThunk<FirstCategoryListApiResult['data'], void, {rejectValue: ApiResult<null>}>(
    'firstCategory/data', 
    async (_, thunkApi) => {
      try{
        const firstCategoryUrl = BASE_URL_CONTEXT + '/catalog/first-categories';
        const response = await baseAxios.get(firstCategoryUrl);

         if (response.data.errno !== 0) {
          return thunkApi.rejectWithValue({
            errno: response.data.errno,
            errmsg: response.data.errmsg,
            data: null,
          });
        }
        //return response.data.data as FirstCategoryListApiResult;
        return response.data.data;
      }catch(error){
         return thunkApi.rejectWithValue({
        errno: 500,
        errmsg: error.message,
        data: null,
      });
    }

  //Make sure the data structure matches what we expect
  //return response.data.data as FirstCategoryListApiResult;
  });

export const getSecondCategories = createAsyncThunk<SecondCategoryApiResult['data'], number, { rejectValue: ApiResult<null> }>(
  'secondCategory/data',
  async (categoryId: number, thunkApi) => {
    try {
      const currentCategoryUrl = BASE_URL_CONTEXT + '/catalog/getsecondcategory?id=' + categoryId;
      const response = await baseAxios.get(currentCategoryUrl);
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
export const goodsBySubCategoryId = createAsyncThunk<GoodCategoryResult, number, { rejectValue: ApiResult<null> }>(
  'goods/goodsByCategoryId',
  async (subCategoryId: number, thunkApi) => {
    try {
      const goodBySubCategoryUrl = BASE_URL_CONTEXT + '/catalog/goods?id=' + subCategoryId;
      const response = await axios.get(goodBySubCategoryUrl);

      if (response.data) {
        return response.data.data;
      } else {
        return new Error('Invalid response from server');
      }
    } catch (error) {
      return thunkApi.rejectWithValue({
        errno: 500,
        errmsg: error.message,
        data: null,
      });
    }
  }
);

interface CategoryState
  extends BaseState<{
    dataCategoryIndex: CategoryIndexApiResult['data'];
    currentCatalogData: CategoryCurrentApiResult['data'];
    dataFirstCategories: FirstCategoryListApiResult['data'];
    dataGoodsByCategoryId: GoodCategoryResult['data'];
    defaultFirstGoodsSubCategory: GoodCategoryResult['data'];
    dataSecondCategories: SecondCategoryApiResult['data'];
  }> {}

const initialState: CategoryState = {
  loading: 'idle',
  errorMessage: null,
  data: {
    dataCategoryIndex: {
      currentCategory: null,
      categoryList: [],
      subCategoryList: [],
    },

    dataFirstCategories:{
      l1CatList: [],
    },
    defaultFirstGoodsSubCategory: {
      currentCategory: null,
      goodsCategory: [],
    },
    currentCatalogData: {
      currentCategory: null,
      currentSubCategory: [],
    },
    dataGoodsByCategoryId: {
      currentCategory: null,
      goodsCategory: [],
    },
    dataSecondCategories: {
      currentCategory: null,
      secondCategories: [],
    },
  },
  errorNumber: null,
};

const categorySlice = createSlice({
  name: 'categoryState',
  initialState,
  reducers: {},
  extraReducers: builder => {
    builder
      /* .addCase(getCatalogIndexData.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.data.dataCategoryIndex = action.payload;
      }) */

      .addCase(getFirstCategories.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.data.dataFirstCategories = action.payload;
      }) 
      .addCase(getCurrentCatalogData.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.data.currentCatalogData = action.payload;
      })
      .addCase(goodsBySubCategoryId.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.data.dataGoodsByCategoryId = action.payload.data;
      })
      .addCase(getSecondCategories.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.data.dataSecondCategories = action.payload;
      })
      .addCase(getGoodsOfDefaultFirstSubCategory.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.data.defaultFirstGoodsSubCategory = action.payload.data;
      });
  },
});

//export const {} = homeSlice.actions;
export default categorySlice.reducer;
