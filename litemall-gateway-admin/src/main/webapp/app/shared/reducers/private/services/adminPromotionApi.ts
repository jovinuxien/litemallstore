import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { IAd, ICoupon, ICouponUser, IGrouponRecord, IGrouponRule, PagedList } from 'app/shared/model/admin/promotion-system.model';
import { getAdminToken } from 'app/shared/reducers/admin-auth';

// RTK Query client for the admin promotion verticals (ad / coupon / groupon),
// served through the gateway under '/srv/private/admin/*'. These endpoints are
// hosted at the gateway edge (litemall-gatewayadmin/web/admin/*) — the legacy
// litemall-admin-api is deliberately unrouted, and the DDD promotion-service
// does not own admin CRUD yet (follow-up: migrate to promotion-service when
// fix/promotion lands, no SPA change beyond the base). Admin JWT attached as a
// Bearer token; the edge enforces ROLE_ADMIN on '/srv/private/admin/**'.

export interface ListParams {
  page: number;
  limit: number;
  sort: string;
  order: 'asc' | 'desc';
}

export interface AdListParams extends ListParams {
  name?: string;
  content?: string;
}
export interface CouponListParams extends ListParams {
  name?: string;
  type?: number;
  status?: number;
}
export interface CouponUserListParams extends ListParams {
  couponId?: number;
  userId?: number;
  status?: number;
}
export interface GrouponRuleListParams extends ListParams {
  goodsId?: string;
}
export interface GrouponRecordListParams extends ListParams {
  grouponRuleId?: string;
}

export interface ApiEnvelope<T = unknown> {
  errno: number;
  errmsg: string;
  data?: T;
}

const emptyPage = <T>(): PagedList<T> => ({ list: [], total: 0, page: 1, limit: 0, pages: 0 });

const clean = (params: Record<string, unknown>): Record<string, unknown> => {
  const out: Record<string, unknown> = {};
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') out[k] = v;
  });
  return out;
};

export const adminPromotionApi = createApi({
  reducerPath: 'adminPromotionApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      return headers;
    },
  }),
  tagTypes: ['Ad', 'Coupon', 'GrouponRule'],
  endpoints: builder => ({
    // ----- Ad -----------------------------------------------------------
    listAds: builder.query<PagedList<IAd>, AdListParams>({
      query: ({ page, limit, sort, order, name, content }) => ({ url: '/ad/list', params: clean({ page, limit, sort, order, name, content }) }),
      transformResponse: (r: ApiEnvelope<PagedList<IAd>>) => r?.data ?? emptyPage<IAd>(),
      providesTags: ['Ad'],
    }),
    readAd: builder.query<IAd, number | string>({
      query: id => ({ url: '/ad/read', params: { id } }),
      transformResponse: (r: ApiEnvelope<IAd>) => r?.data ?? {},
    }),
    createAd: builder.mutation<ApiEnvelope<IAd>, IAd>({
      query: body => ({ url: '/ad/create', method: 'POST', body }),
      invalidatesTags: ['Ad'],
    }),
    updateAd: builder.mutation<ApiEnvelope<IAd>, IAd>({
      query: body => ({ url: '/ad/update', method: 'POST', body }),
      invalidatesTags: ['Ad'],
    }),
    deleteAd: builder.mutation<ApiEnvelope, IAd>({
      query: body => ({ url: '/ad/delete', method: 'POST', body }),
      invalidatesTags: ['Ad'],
    }),

    // ----- Coupon -------------------------------------------------------
    listCoupons: builder.query<PagedList<ICoupon>, CouponListParams>({
      query: ({ page, limit, sort, order, name, type, status }) => ({ url: '/coupon/list', params: clean({ page, limit, sort, order, name, type, status }) }),
      transformResponse: (r: ApiEnvelope<PagedList<ICoupon>>) => r?.data ?? emptyPage<ICoupon>(),
      providesTags: ['Coupon'],
    }),
    listCouponUsers: builder.query<PagedList<ICouponUser>, CouponUserListParams>({
      query: ({ page, limit, sort, order, couponId, userId, status }) => ({
        url: '/coupon/listuser',
        params: clean({ page, limit, sort, order, couponId, userId, status }),
      }),
      transformResponse: (r: ApiEnvelope<PagedList<ICouponUser>>) => r?.data ?? emptyPage<ICouponUser>(),
    }),
    readCoupon: builder.query<ICoupon, number | string>({
      query: id => ({ url: '/coupon/read', params: { id } }),
      transformResponse: (r: ApiEnvelope<ICoupon>) => r?.data ?? {},
    }),
    createCoupon: builder.mutation<ApiEnvelope<ICoupon>, ICoupon>({
      query: body => ({ url: '/coupon/create', method: 'POST', body }),
      invalidatesTags: ['Coupon'],
    }),
    updateCoupon: builder.mutation<ApiEnvelope<ICoupon>, ICoupon>({
      query: body => ({ url: '/coupon/update', method: 'POST', body }),
      invalidatesTags: ['Coupon'],
    }),
    deleteCoupon: builder.mutation<ApiEnvelope, ICoupon>({
      query: body => ({ url: '/coupon/delete', method: 'POST', body }),
      invalidatesTags: ['Coupon'],
    }),

    // ----- Groupon rules + activity -------------------------------------
    listGrouponRules: builder.query<PagedList<IGrouponRule>, GrouponRuleListParams>({
      query: ({ page, limit, sort, order, goodsId }) => ({ url: '/groupon/list', params: clean({ page, limit, sort, order, goodsId }) }),
      transformResponse: (r: ApiEnvelope<PagedList<IGrouponRule>>) => r?.data ?? emptyPage<IGrouponRule>(),
      providesTags: ['GrouponRule'],
    }),
    createGrouponRule: builder.mutation<ApiEnvelope<IGrouponRule>, IGrouponRule>({
      query: body => ({ url: '/groupon/create', method: 'POST', body }),
      invalidatesTags: ['GrouponRule'],
    }),
    updateGrouponRule: builder.mutation<ApiEnvelope<IGrouponRule>, IGrouponRule>({
      query: body => ({ url: '/groupon/update', method: 'POST', body }),
      invalidatesTags: ['GrouponRule'],
    }),
    deleteGrouponRule: builder.mutation<ApiEnvelope, IGrouponRule>({
      query: body => ({ url: '/groupon/delete', method: 'POST', body }),
      invalidatesTags: ['GrouponRule'],
    }),
    listGrouponRecords: builder.query<PagedList<IGrouponRecord>, GrouponRecordListParams>({
      query: ({ page, limit, sort, order, grouponRuleId }) => ({
        url: '/groupon/listRecord',
        params: clean({ page, limit, sort, order, grouponRuleId }),
      }),
      transformResponse: (r: ApiEnvelope<PagedList<IGrouponRecord>>) => r?.data ?? emptyPage<IGrouponRecord>(),
    }),
  }),
});

export const {
  useListAdsQuery,
  useReadAdQuery,
  useCreateAdMutation,
  useUpdateAdMutation,
  useDeleteAdMutation,
  useListCouponsQuery,
  useListCouponUsersQuery,
  useReadCouponQuery,
  useCreateCouponMutation,
  useUpdateCouponMutation,
  useDeleteCouponMutation,
  useListGrouponRulesQuery,
  useCreateGrouponRuleMutation,
  useUpdateGrouponRuleMutation,
  useDeleteGrouponRuleMutation,
  useListGrouponRecordsQuery,
} = adminPromotionApi;
