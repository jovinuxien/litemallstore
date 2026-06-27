import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { IBrand, ICategory, ICategoryL1Option, ICategoryVo, IComment, IIssue, IKeyword, PagedList } from 'app/shared/model/admin/catalog.model';
import { IOrderDetail, IOrderVo } from 'app/shared/model/admin/order.model';
import { getAdminToken } from 'app/shared/reducers/admin-auth';

// RTK Query API for the admin catalogue/order endpoints, served through the
// gateway under '/admin/*' (litemall-admin-api) as an authenticated admin. The
// admin JWT is attached as a Bearer token (prepareHeaders); the edge enforces
// ROLE_ADMIN on '/admin/**' and relays a trusted identity downstream
// (MachineTokenRelayFilter). No legacy admin-token header, no hardcoded host.
// One client covers brand / category / comment / keyword / issue / order;
// mutations invalidate the matching list tag so the table refetches.
//
// NOTE: the catalogue/order data lives in litemall-admin-api, already on the
// sanctioned '/admin/**' route. A future migration of these endpoints into the
// DDD services (goods-management/order) is a follow-up, out of scope here.

export interface ListParams {
  page: number;
  limit: number;
  sort: string;
  order: 'asc' | 'desc';
}

export interface BrandListParams extends ListParams {
  name?: string;
}
export interface KeywordListParams extends ListParams {
  keyword?: string;
}
export interface IssueListParams extends ListParams {
  question?: string;
}
export interface CommentListParams extends ListParams {
  userId?: string;
  valueId?: string;
}
export interface OrderListParams extends ListParams {
  orderSn?: string;
  nickname?: string;
  consignee?: string;
  orderStatusArray?: number[];
}

// The litemall envelope: { errno, errmsg, data }.
export interface ApiEnvelope<T = unknown> {
  errno: number;
  errmsg: string;
  data?: T;
}

const emptyPage = <T>(): PagedList<T> => ({ list: [], total: 0, page: 1, limit: 0, pages: 0 });

// Drop empty/undefined query params so we don't send "name=".
const clean = (params: Record<string, unknown>): Record<string, unknown> => {
  const out: Record<string, unknown> = {};
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') out[k] = v;
  });
  return out;
};

export const adminCatalogApi = createApi({
  reducerPath: 'adminCatalogApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/admin',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) {
        headers.set('Authorization', `Bearer ${token}`);
      }
      return headers;
    },
  }),
  tagTypes: ['Brand', 'Category', 'Comment', 'Keyword', 'Issue', 'Order'],
  endpoints: builder => ({
    // ----- Brand --------------------------------------------------------
    listBrands: builder.query<PagedList<IBrand>, BrandListParams>({
      query: ({ page, limit, sort, order, name }) => ({ url: '/brand/list', params: clean({ page, limit, sort, order, name }) }),
      transformResponse: (r: ApiEnvelope<PagedList<IBrand>>) => r?.data ?? emptyPage<IBrand>(),
      providesTags: ['Brand'],
    }),
    readBrand: builder.query<IBrand, number | string>({
      query: id => ({ url: '/brand/read', params: { id } }),
      transformResponse: (r: ApiEnvelope<IBrand>) => r?.data ?? {},
    }),
    createBrand: builder.mutation<ApiEnvelope<IBrand>, IBrand>({
      query: body => ({ url: '/brand/create', method: 'POST', body }),
      invalidatesTags: ['Brand'],
    }),
    updateBrand: builder.mutation<ApiEnvelope<IBrand>, IBrand>({
      query: body => ({ url: '/brand/update', method: 'POST', body }),
      invalidatesTags: ['Brand'],
    }),
    deleteBrand: builder.mutation<ApiEnvelope, IBrand>({
      query: body => ({ url: '/brand/delete', method: 'POST', body }),
      invalidatesTags: ['Brand'],
    }),

    // ----- Category -----------------------------------------------------
    listCategories: builder.query<ICategoryVo[], void>({
      query: () => ({ url: '/category/list' }),
      transformResponse: (r: ApiEnvelope<PagedList<ICategoryVo>>) => r?.data?.list ?? [],
      providesTags: ['Category'],
    }),
    categoryL1: builder.query<ICategoryL1Option[], void>({
      query: () => ({ url: '/category/l1' }),
      transformResponse: (r: ApiEnvelope<PagedList<ICategoryL1Option>>) => r?.data?.list ?? [],
    }),
    readCategory: builder.query<ICategory, number | string>({
      query: id => ({ url: '/category/read', params: { id } }),
      transformResponse: (r: ApiEnvelope<ICategory>) => r?.data ?? {},
    }),
    createCategory: builder.mutation<ApiEnvelope<ICategory>, ICategory>({
      query: body => ({ url: '/category/create', method: 'POST', body }),
      invalidatesTags: ['Category'],
    }),
    updateCategory: builder.mutation<ApiEnvelope<ICategory>, ICategory>({
      query: body => ({ url: '/category/update', method: 'POST', body }),
      invalidatesTags: ['Category'],
    }),
    deleteCategory: builder.mutation<ApiEnvelope, ICategory>({
      query: body => ({ url: '/category/delete', method: 'POST', body }),
      invalidatesTags: ['Category'],
    }),

    // ----- Comment (list + delete only) ---------------------------------
    listComments: builder.query<PagedList<IComment>, CommentListParams>({
      query: ({ page, limit, sort, order, userId, valueId }) => ({
        url: '/comment/list',
        params: clean({ page, limit, sort, order, userId, valueId }),
      }),
      transformResponse: (r: ApiEnvelope<PagedList<IComment>>) => r?.data ?? emptyPage<IComment>(),
      providesTags: ['Comment'],
    }),
    deleteComment: builder.mutation<ApiEnvelope, IComment>({
      query: body => ({ url: '/comment/delete', method: 'POST', body }),
      invalidatesTags: ['Comment'],
    }),

    // ----- Keyword ------------------------------------------------------
    listKeywords: builder.query<PagedList<IKeyword>, KeywordListParams>({
      query: ({ page, limit, sort, order, keyword }) => ({ url: '/keyword/list', params: clean({ page, limit, sort, order, keyword }) }),
      transformResponse: (r: ApiEnvelope<PagedList<IKeyword>>) => r?.data ?? emptyPage<IKeyword>(),
      providesTags: ['Keyword'],
    }),
    readKeyword: builder.query<IKeyword, number | string>({
      query: id => ({ url: '/keyword/read', params: { id } }),
      transformResponse: (r: ApiEnvelope<IKeyword>) => r?.data ?? {},
    }),
    createKeyword: builder.mutation<ApiEnvelope<IKeyword>, IKeyword>({
      query: body => ({ url: '/keyword/create', method: 'POST', body }),
      invalidatesTags: ['Keyword'],
    }),
    updateKeyword: builder.mutation<ApiEnvelope<IKeyword>, IKeyword>({
      query: body => ({ url: '/keyword/update', method: 'POST', body }),
      invalidatesTags: ['Keyword'],
    }),
    deleteKeyword: builder.mutation<ApiEnvelope, IKeyword>({
      query: body => ({ url: '/keyword/delete', method: 'POST', body }),
      invalidatesTags: ['Keyword'],
    }),

    // ----- Issue --------------------------------------------------------
    listIssues: builder.query<PagedList<IIssue>, IssueListParams>({
      query: ({ page, limit, sort, order, question }) => ({ url: '/issue/list', params: clean({ page, limit, sort, order, question }) }),
      transformResponse: (r: ApiEnvelope<PagedList<IIssue>>) => r?.data ?? emptyPage<IIssue>(),
      providesTags: ['Issue'],
    }),
    readIssue: builder.query<IIssue, number | string>({
      query: id => ({ url: '/issue/read', params: { id } }),
      transformResponse: (r: ApiEnvelope<IIssue>) => r?.data ?? {},
    }),
    createIssue: builder.mutation<ApiEnvelope<IIssue>, IIssue>({
      query: body => ({ url: '/issue/create', method: 'POST', body }),
      invalidatesTags: ['Issue'],
    }),
    updateIssue: builder.mutation<ApiEnvelope<IIssue>, IIssue>({
      query: body => ({ url: '/issue/update', method: 'POST', body }),
      invalidatesTags: ['Issue'],
    }),
    deleteIssue: builder.mutation<ApiEnvelope, IIssue>({
      query: body => ({ url: '/issue/delete', method: 'POST', body }),
      invalidatesTags: ['Issue'],
    }),

    // ----- Order (read-only: list + detail) -----------------------------
    listOrders: builder.query<PagedList<IOrderVo>, OrderListParams>({
      query: ({ page, limit, sort, order, orderSn, nickname, consignee, orderStatusArray }) => ({
        url: '/order/list',
        params: clean({ page, limit, sort, order, orderSn, nickname, consignee, orderStatusArray }),
      }),
      transformResponse: (r: ApiEnvelope<PagedList<IOrderVo>>) => r?.data ?? emptyPage<IOrderVo>(),
      providesTags: ['Order'],
    }),
    readOrder: builder.query<IOrderDetail, number | string>({
      query: id => ({ url: '/order/detail', params: { id } }),
      transformResponse: (r: ApiEnvelope<IOrderDetail>) => r?.data ?? {},
    }),
  }),
});

export const {
  useListBrandsQuery,
  useReadBrandQuery,
  useCreateBrandMutation,
  useUpdateBrandMutation,
  useDeleteBrandMutation,
  useListCategoriesQuery,
  useCategoryL1Query,
  useReadCategoryQuery,
  useCreateCategoryMutation,
  useUpdateCategoryMutation,
  useDeleteCategoryMutation,
  useListCommentsQuery,
  useDeleteCommentMutation,
  useListKeywordsQuery,
  useReadKeywordQuery,
  useCreateKeywordMutation,
  useUpdateKeywordMutation,
  useDeleteKeywordMutation,
  useListIssuesQuery,
  useReadIssueQuery,
  useCreateIssueMutation,
  useUpdateIssueMutation,
  useDeleteIssueMutation,
  useListOrdersQuery,
  useReadOrderQuery,
} = adminCatalogApi;
