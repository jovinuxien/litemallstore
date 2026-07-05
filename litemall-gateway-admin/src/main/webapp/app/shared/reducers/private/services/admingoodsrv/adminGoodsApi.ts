import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { IGood, IGoodsDetail, ProductList, SpecificationList } from 'app/shared/model/product/product.model';
import { getAdminToken } from 'app/shared/reducers/admin-auth';

// RTK Query API for admin goods, served through the gateway under
// '/srv/private/admin/goods' as an authenticated admin. The admin JWT is
// attached as a Bearer token (prepareHeaders); the edge enforces ROLE_ADMIN on
// '/srv/private/admin/**' and relays a trusted identity downstream. No legacy
// admin-token header, no hardcoded host.
//
// NOTE: the exact JSON the goods-management service returns for these admin
// endpoints is owned by the `goods-management` worktree. This client codes the
// agreed shape (list rows as IGood incl. status/salesQuantity/brand/
// categoryNames; detail as goods + specs + per-SKU products incl. stock); any
// divergence is a goods-management follow-up (see ROUTING.md).

export interface AdminGoodsListResponse {
  total: number;
  pages: number;
  limit: number;
  page: number;
  list: IGood[];
}

export interface AdminGoodsListParams {
  page: number;
  limit: number;
  sort: string;
  order: 'asc' | 'desc';
}

export interface AdminGoodsDetail {
  goods: IGoodsDetail;
  specificationList: SpecificationList[];
  products: ProductList[];
  categoryNames?: string[];
  brand?: string;
}

// The litemall envelope: { errno, errmsg, data }.
interface ApiEnvelope<T> {
  errno: number;
  errmsg: string;
  data: T;
}

// The write payload of the backend AdminGoodsController: the goods row plus its
// per-SKU products, specifications and attributes (GoodsAllinone).
export interface GoodsAllinone {
  goods: Record<string, unknown>;
  products: Record<string, unknown>[];
  specifications: Record<string, unknown>[];
  attributes: Record<string, unknown>[];
}

export interface BatchCreateResult {
  created: number;
  failed: { index: number; name?: string; error: string }[];
}

export interface PickOption {
  value: number;
  label: string;
  children?: PickOption[];
}

export interface CatAndBrand {
  categoryList: PickOption[];
  brandList: PickOption[];
}

export const adminGoodsApi = createApi({
  reducerPath: 'adminGoodsApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin/goods',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) {
        headers.set('Authorization', `Bearer ${token}`);
      }
      return headers;
    },
  }),
  tagTypes: ['Goods'],
  endpoints: builder => ({
    getAdminGoodsList: builder.query<AdminGoodsListResponse, AdminGoodsListParams>({
      query: ({ page, limit, sort, order }) => ({
        url: '/list',
        params: { page, limit, sort, order },
      }),
      transformResponse: (response: ApiEnvelope<AdminGoodsListResponse>) =>
        response?.data ?? { total: 0, pages: 0, limit: 0, page: 1, list: [] },
      providesTags: ['Goods'],
    }),
    getAdminGoodsDetail: builder.query<AdminGoodsDetail, number | string>({
      query: id => ({
        url: '/detail',
        params: { id },
      }),
      transformResponse: (response: ApiEnvelope<AdminGoodsDetail>) => response?.data,
    }),
    getCatAndBrand: builder.query<CatAndBrand, void>({
      query: () => ({ url: '/catAndBrand' }),
      transformResponse: (response: ApiEnvelope<CatAndBrand>) => response?.data ?? { categoryList: [], brandList: [] },
    }),
    createGoods: builder.mutation<ApiEnvelope<unknown>, GoodsAllinone>({
      query: body => ({ url: '/create', method: 'POST', body }),
      invalidatesTags: ['Goods'],
    }),
    updateGoods: builder.mutation<ApiEnvelope<unknown>, GoodsAllinone>({
      query: body => ({ url: '/update', method: 'POST', body }),
      invalidatesTags: ['Goods'],
    }),
    deleteGoods: builder.mutation<ApiEnvelope<unknown>, { id: number }>({
      query: body => ({ url: '/delete', method: 'POST', body }),
      invalidatesTags: ['Goods'],
    }),
    batchCreateGoods: builder.mutation<ApiEnvelope<BatchCreateResult>, GoodsAllinone[]>({
      query: body => ({ url: '/batch-create', method: 'POST', body }),
      invalidatesTags: ['Goods'],
    }),
  }),
});

export const {
  useGetAdminGoodsListQuery,
  useGetAdminGoodsDetailQuery,
  useGetCatAndBrandQuery,
  useCreateGoodsMutation,
  useUpdateGoodsMutation,
  useDeleteGoodsMutation,
  useBatchCreateGoodsMutation,
} = adminGoodsApi;
