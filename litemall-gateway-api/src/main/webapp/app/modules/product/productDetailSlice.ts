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

// Prices arrive as LitemallMoney { amount } or a plain number.
const priceNum = (price: unknown): number => {
  if (price == null) return 0;
  if (typeof price === 'number') return price;
  if (typeof price === 'object' && 'amount' in (price as Record<string, unknown>)) {
    return Number((price as { amount: unknown }).amount) || 0;
  }
  const n = Number(price);
  return Number.isFinite(n) ? n : 0;
};

// Read an id whether it's flat (`5`) or wrapped in a value object (`{ id: 5 }`).
const idOf = (v: unknown): number => {
  if (v == null) return 0;
  if (typeof v === 'object' && 'id' in (v as Record<string, unknown>)) return Number((v as { id: unknown }).id) || 0;
  return Number(v) || 0;
};

/**
 * The DDD goods service returns
 *   data: { categoryIds, goods, attributes, specifications, products }
 * where `goods` uses value objects (`goodsId:{id}`, `goodsName`, prices as
 * `{amount}`) and the SPA's detail view expects the flat legacy shape
 * `{ info, productList, attribute, ... }`. Normalize here so Detail.tsx and the
 * cart get plain ids/numbers.
 */
const normalizeDetail = (raw: any): ProductDetailApiResult['data'] => {
  const g = raw?.goods ?? {};
  const info = {
    id: idOf(g.goodsId),
    goodsSn: g.goodsSn ?? '',
    name: g.goodsName ?? g.name ?? '',
    categoryId: idOf(g.categoryId),
    brandId: idOf(g.manufacturerId ?? g.brandId),
    gallery: Array.isArray(g.gallery) ? g.gallery : [],
    keywords: g.keyword ?? g.keywords ?? '',
    brief: g.brief ?? '',
    isOnSale: g.onSale ?? g.isOnSale ?? true,
    sortOrder: g.sortOrder ?? 0,
    picUrl: g.picUrl ?? '',
    shareUrl: g.shareUrl ?? '',
    isNew: g.new ?? g.isNew ?? false,
    isHot: g.hot ?? g.isHot ?? false,
    unit: g.unit ?? '',
    counterPrice: priceNum(g.counterPrice),
    retailPrice: priceNum(g.retailPrice),
    addTime: g.addTime ?? null,
    updateTime: g.updateTime ?? null,
    deleted: g.deleted ?? false,
    detail: g.detail ?? '',
  };
  const productList = (Array.isArray(raw?.products) ? raw.products : []).map((p: any) => ({
    id: idOf(p.goodsProductId),
    goodsId: idOf(p.goodsId),
    specifications: Array.isArray(p.specifications) ? p.specifications : [],
    price: priceNum(p.price),
    number: Number(p.number) || 0,
    url: p.url ?? '',
    addTime: p.addTime ?? null,
    updateTime: p.updateTime ?? null,
    deleted: p.deleted ?? false,
  }));
  const attribute = (Array.isArray(raw?.attributes) ? raw.attributes : []).map((a: any) => ({
    id: idOf(a.goodsAttributeId),
    goodsId: idOf(a.goodsId),
    attribute: a.attributeName ?? a.attribute ?? '',
    value: a.attributeValue ?? a.value ?? '',
    addTime: a.addTime ?? null,
    updateTime: a.updateTime ?? null,
    deleted: a.deleted ?? false,
  }));
  return {
    specificationList: Array.isArray(raw?.specifications) ? raw.specifications : [],
    groupon: [],
    issue: [],
    shareImage: g.shareUrl ?? '',
    comments: { data: [], count: 0 },
    attribute,
    productList,
    info,
  } as unknown as ProductDetailApiResult['data'];
};

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
      return normalizeDetail(response.data.data);
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
      // Keep initial state serializable — the backend sends addTime/updateTime as
      // arrays; Detail.tsx never reads them, so null is fine here.
      addTime: null as unknown as Date,
      updateTime: null as unknown as Date,
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
