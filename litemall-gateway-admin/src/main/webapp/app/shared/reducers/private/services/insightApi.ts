import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { getAdminToken } from 'app/shared/reducers/admin-auth';

// Wave 12: RTK Query client for the CJ inventory-insight admin surface,
// served by goods-management under '/srv/private/admin/insight' (the
// gateway's '/srv/**' catch-all — no route change). Coded to the Wave-12
// CONTRACT, not to the goods-management branch: money fields are plain
// decimals in the litemall {errno,errmsg,data} envelope, and every margin
// field is `null` (never 0) while the goods' CJ cost has not been captured
// yet — the views render those as an explicit "—".

export type InsightSortKey = 'add_time' | 'retail_price' | 'margin_pct' | 'stock' | 'sales';

export interface ICategoryInsight {
  categoryId: number;
  name: string;
  onSaleCount: number;
  newArrivals7d: number;
  stockUnits: number;
  lowStockCount: number;
  unavailableCount: number;
  avgMarginPct: number | null;
  potentialProfit: number | null;
}

// Goods summary + insight extras for one row of /insight/goods/list.
export interface IInsightGoodsRow {
  id: number;
  name?: string;
  picUrl?: string;
  retailPrice?: number;
  cost: number | null;
  marginAmount: number | null;
  marginPct: number | null;
  stockTotal?: number;
  cjAvailable?: boolean;
  arrivalDate?: string;
  salesQty?: number;
  dealStatus?: string;
}

export interface InsightGoodsListParams {
  categoryId: number | string;
  sort: InsightSortKey;
  order: 'asc' | 'desc';
  page: number;
  limit: number;
}

export interface InsightGoodsListResponse {
  total: number;
  pages: number;
  limit: number;
  page: number;
  list: IInsightGoodsRow[];
}

export interface IInsightVariant {
  productId: number;
  cjVid?: string;
  specifications?: string[];
  price?: number;
  cost: number | null;
  stock?: number;
  available?: boolean;
}

export interface IInsightSeriesPoint {
  day: string;
  retailPrice?: number;
  cost?: number | null;
  marginPct?: number | null;
  stockTotal?: number;
  available?: boolean | number;
  views?: number;
  salesQty?: number;
}

export interface IInsightTotals {
  views?: number;
  salesQty?: number;
  revenue?: number;
  collects?: number;
  comments?: number;
}

export interface IInsightRecommendation {
  suggestedRetail?: number;
  marginPct?: number | null;
  advertisable?: boolean;
  reasons?: string[];
}

// Existing/proposed deals attached to the goods; the contract leaves the row
// shape open ("deals:[...]"), so keep it defensive.
export interface IInsightDeal {
  id?: number;
  dealPrice?: number;
  suggestedDealPrice?: number;
  startTime?: string;
  stopTime?: string;
  status?: string;
  tier?: string;
  [key: string]: unknown;
}

export interface IInsightGoodsDetail {
  goods: {
    id?: number;
    name?: string;
    picUrl?: string;
    retailPrice?: number;
    cost?: number | null;
    isOnSale?: boolean;
    [key: string]: unknown;
  };
  variants: IInsightVariant[];
  series: IInsightSeriesPoint[];
  totals: IInsightTotals;
  deals: IInsightDeal[];
  recommendation?: IInsightRecommendation;
}

export interface IDealCandidate {
  goodsId: number;
  name?: string;
  picUrl?: string;
  day?: string;
  tier?: string;
  score?: number;
  cost: number | null;
  retailPrice?: number;
  suggestedDealPrice?: number;
  stockTotal?: number;
  rating?: number;
  status?: string;
  reasons?: string[];
}

export interface ApproveDealCommand {
  goodsId: number;
  dealPrice: number;
  startTime: string;
  stopTime: string;
  stock: number;
}

interface ApiEnvelope<T> {
  errno: number;
  errmsg: string;
  data?: T;
}

export const insightApi = createApi({
  reducerPath: 'insightApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin/insight',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) {
        headers.set('Authorization', `Bearer ${token}`);
      }
      return headers;
    },
  }),
  tagTypes: ['Categories', 'InsightGoods', 'Candidates'],
  endpoints: builder => ({
    // Sorted potentialProfit desc by the server; L1 roots with on-sale goods only.
    getInsightCategories: builder.query<{ list: ICategoryInsight[] }, void>({
      query: () => ({ url: '/categories' }),
      transformResponse: (r: ApiEnvelope<{ list: ICategoryInsight[] }>) => r?.data ?? { list: [] },
      providesTags: ['Categories'],
    }),
    getInsightGoodsList: builder.query<InsightGoodsListResponse, InsightGoodsListParams>({
      query: ({ categoryId, sort, order, page, limit }) => ({
        url: '/goods/list',
        params: { categoryId, sort, order, page, limit },
      }),
      transformResponse: (r: ApiEnvelope<InsightGoodsListResponse>) => r?.data ?? { total: 0, pages: 0, limit: 0, page: 1, list: [] },
      providesTags: ['InsightGoods'],
    }),
    getInsightGoodsDetail: builder.query<IInsightGoodsDetail | undefined, number | string>({
      query: id => ({ url: `/goods/${id}` }),
      transformResponse: (r: ApiEnvelope<IInsightGoodsDetail>) => r?.data,
      providesTags: (result, err, id) => [{ type: 'InsightGoods', id }],
    }),
    getDealCandidates: builder.query<{ list: IDealCandidate[] }, { day?: string }>({
      query: ({ day }) => ({ url: '/deal-candidates', params: day ? { day } : undefined }),
      transformResponse: (r: ApiEnvelope<{ list: IDealCandidate[] }>) => r?.data ?? { list: [] },
      providesTags: ['Candidates'],
    }),
    // Creates the flash deal through the CJ-unparked path; the backend
    // refuses a deal price below cost — the dialog pre-validates the same.
    approveDealCandidate: builder.mutation<ApiEnvelope<unknown>, ApproveDealCommand>({
      query: ({ goodsId, ...body }) => ({ url: `/deal-candidates/${goodsId}/approve`, method: 'POST', body }),
      invalidatesTags: (result, err, { goodsId }) => ['Candidates', { type: 'InsightGoods', id: goodsId }],
    }),
    dismissDealCandidate: builder.mutation<ApiEnvelope<unknown>, { goodsId: number }>({
      query: ({ goodsId }) => ({ url: `/deal-candidates/${goodsId}/dismiss`, method: 'POST' }),
      invalidatesTags: (result, err, { goodsId }) => ['Candidates', { type: 'InsightGoods', id: goodsId }],
    }),
  }),
});

export const {
  useGetInsightCategoriesQuery,
  useGetInsightGoodsListQuery,
  useGetInsightGoodsDetailQuery,
  useGetDealCandidatesQuery,
  useApproveDealCandidateMutation,
  useDismissDealCandidateMutation,
} = insightApi;
