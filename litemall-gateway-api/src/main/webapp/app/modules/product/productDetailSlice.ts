import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { ApiResult, BaseState } from 'app/config/types';

/**
 * Customer product-detail data, mapped to the REAL goods-management contract.
 *
 * `GET /srv/goods/detail?id=` -> goodsManagementService.goodsDetail(...) returns
 *   { errno, errmsg, data: {
 *       goods:          LitemallGoodsAggregate,                  // the product
 *       products:       LitemallGoodsProductAggregate[],         // SKUs / variants
 *       specifications: LitemallGoodsSpecificationAggregate[],   // option groups
 *       attributes:     LitemallGoodsAttributeAggregate[],       // spec sheet
 *       categoryIds:    [parentCategoryId, leafCategoryId]
 *   }}
 *
 * The aggregates serialize the embedded value objects verbatim — ids as
 * `{ id }`, money as `LitemallMoney { amount }`, and the boolean flags through
 * their `is*` getters (`onSale` / `hot` / `new`). The view layer reads those
 * defensively (priceNum / goodId in ProductCard); the types below mirror the
 * wire shape so the detail page can group variants and render every field.
 *
 * NOTE: the previous model read `info` / `productList` / `attribute`, none of
 * which exist in this payload — the detail page rendered "Product not found"
 * against the live backend. This is the fix.
 */
export interface DetailMoney {
  amount: number;
}

export interface DetailGoods {
  goodsId?: { id?: number } | number;
  goodsSn?: string;
  goodsName?: string;
  categoryId?: { id?: number } | number;
  manufacturerId?: { id?: number } | number;
  gallery?: string[];
  keyword?: string;
  brief?: string;
  onSale?: boolean;
  sortOrder?: number;
  picUrl?: string;
  shareUrl?: string;
  hot?: boolean;
  new?: boolean;
  unit?: string;
  counterPrice?: DetailMoney | number | null;
  retailPrice?: DetailMoney | number | null;
  detail?: string;
  // Catalog origin: 'local' or 'cj' (CJ Dropshipping); the legacy DB-served CJ detail tags
  // 'cj_dropshipping'. Drives the storefront's CJ-line detection at checkout.
  source?: string;
}

export interface DetailProduct {
  goodsProductId?: { id?: string } | string;
  specifications: string[];
  price?: DetailMoney | number | null;
  number?: number;
  url?: string;
}

export interface DetailSpecification {
  specifications: string;
  value: string;
  picUrl?: string;
}

export interface DetailAttribute {
  attributeName?: string;
  attributeValue?: string;
}

export interface ProductDetailData {
  goods: DetailGoods | null;
  products: DetailProduct[];
  specifications: DetailSpecification[];
  attributes: DetailAttribute[];
  categoryIds: number[];
  /**
   * Wave 26 Phase 1b: present ONLY when the backend holds a measured, non-zero EU warehouse
   * reading for this product. Absent means "never probed" OR "probed, no EU stock" — the
   * storefront treats both as "show nothing", so absence is never a claim.
   */
  euStock?: { units?: number; countries?: string[] } | null;
}

export const getProductDetail = createAsyncThunk<ProductDetailData, string, { rejectValue: ApiResult<null> }>(
  'product/detail',
  async (goodsId: string, thunkApi) => {
    try {
      const response = await baseAxios.get(`${BASE_URL_CONTEXT}/goods/detail?id=${goodsId}`);
      if (response.data.errno !== 0) {
        return thunkApi.rejectWithValue({ errno: response.data.errno, errmsg: response.data.errmsg, data: null });
      }
      const d = response.data.data ?? {};
      return {
        goods: d.goods ?? null,
        products: d.products ?? [],
        specifications: d.specifications ?? [],
        attributes: d.attributes ?? [],
        categoryIds: d.categoryIds ?? [],
      };
    } catch (error) {
      return thunkApi.rejectWithValue({ errno: 500, errmsg: error.message, data: null });
    }
  }
);

interface ProductDetailState extends BaseState<ProductDetailData> {}

const initialState: ProductDetailState = {
  loading: 'idle',
  errorMessage: null,
  errorNumber: null,
  data: {
    goods: null,
    products: [],
    specifications: [],
    attributes: [],
    categoryIds: [],
  },
};

const productDetailSlice = createSlice({
  name: 'productDetailState',
  initialState,
  reducers: {},
  extraReducers: builder => {
    builder
      .addCase(getProductDetail.pending, state => {
        state.loading = 'pending';
        state.errorMessage = null;
      })
      .addCase(getProductDetail.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.data = action.payload;
      })
      .addCase(getProductDetail.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = action.payload?.errmsg ?? 'Failed to load product';
        state.errorNumber = action.payload?.errno ?? 500;
      });
  },
});

export default productDetailSlice.reducer;
