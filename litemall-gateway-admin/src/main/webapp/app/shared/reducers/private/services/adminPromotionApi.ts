import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { IAd, ICombination, ICombinationPink, ICoupon, ICouponUser, PagedList } from 'app/shared/model/admin/promotion-system.model';
import { getAdminToken } from 'app/shared/reducers/admin-auth';
import { fromServerDateTime } from 'app/shared/util/server-datetime';

// RTK Query client for the admin promotion verticals.
//
// - Ads stay at the gateway edge (litemall-gatewayadmin/web/admin/
//   EdgeAdminAdController, legacy {errno,errmsg,data} envelope).
// - Coupons + combinations (group-buy) are owned by promotion-service under
//   /srv/private/admin/promotion/{coupon,combination} (see
//   litemall-promotion-service/docs/spec-gateway-routes.md). That surface is
//   REST-shaped: GET list endpoints return BARE ARRAYS (no total/pages),
//   reads return the DTO or 404, and mutations return
//   {success, operationType, message, data} with HTTP 200/400. Manager DTOs
//   serialize enum fields as display-name strings; the maps below normalise
//   them back to the numeric codes the views/forms use, mirroring the
//   promotion enums (LitemallCouponType/Status/GoodsType/TimeType,
//   LitemallUserCouponStatus, LitemallCombinationStatus,
//   LitemallCombinationPinkStatus).
//
// Admin JWT attached as a Bearer token; the edge gates
// '/srv/private/admin/**' to ROLE_ADMIN and the machine-token relay +
// X-User-* forwarding satisfy promotion's svcsecurity gate downstream.

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

export interface PageParams {
  page: number;
  limit: number;
}

export interface ApiEnvelope<T = unknown> {
  errno: number;
  errmsg: string;
  data?: T;
}

// Promotion-service mutation result (HTTP 400 carries the same shape).
export interface PromotionOperation {
  success: boolean;
  operationType?: string;
  message?: string;
  data?: Record<string, unknown>;
}

// Bare-array list from the promotion admin surface: no total, so pagers fall
// back to the rowCount < limit "has more" heuristic.
export interface BareList<T> {
  list: T[];
}

const emptyPage = <T>(): PagedList<T> => ({ list: [], total: 0, page: 1, limit: 0, pages: 0 });

const clean = (params: Record<string, unknown>): Record<string, unknown> => {
  const out: Record<string, unknown> = {};
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') out[k] = v;
  });
  return out;
};

// ----- enum display-name ↔ numeric-code normalisation ----------------------

const codeOf = (map: Record<number, string>, display?: string | number | null): number | undefined => {
  if (display == null) return undefined;
  if (typeof display === 'number') return display;
  const hit = Object.entries(map).find(([, label]) => label === display);
  return hit ? Number(hit[0]) : undefined;
};

export const COUPON_TYPE = { 0: 'Common', 1: 'Registration', 2: 'Redemption code' };
export const COUPON_STATUS = { 0: 'Normal', 1: 'Expired', 2: 'Used up' };
export const COUPON_GOODS_TYPE = { 0: 'All goods', 1: 'Category', 2: 'Specific goods' };
export const COUPON_TIME_TYPE = { 0: 'Relative days', 1: 'Absolute window' };
export const USER_COUPON_STATUS = { 0: 'Usable', 1: 'Used', 2: 'Expired', 3: 'Withdrawn' };
export const COMBINATION_STATUS = { 0: 'Draft', 1: 'Active', 2: 'Expired', 3: 'Offline' };
export const PINK_STATUS = { 0: 'Pending', 1: 'Success', 2: 'Failed' };

// Promotion-service manager DTO wire shapes (before normalisation). Datetime
// fields arrive as ISO strings OR LocalDateTime arrays (fromServerDateTime).
interface CouponManagerDto extends Omit<ICoupon, 'id' | 'type' | 'status' | 'goodsType' | 'timeType' | 'startTime' | 'endTime'> {
  couponId?: number;
  type?: string;
  status?: string;
  goodsType?: string;
  timeType?: string;
  startTime?: unknown;
  endTime?: unknown;
}
interface UserCouponDto extends Omit<ICouponUser, 'id' | 'status' | 'startTime' | 'endTime' | 'usedTime'> {
  userCouponId?: number;
  status?: string;
  startTime?: unknown;
  endTime?: unknown;
  usedTime?: unknown;
}
interface CombinationManagerDto extends Omit<ICombination, 'id' | 'status' | 'startTime' | 'endTime'> {
  combinationId?: number;
  status?: string;
  startTime?: unknown;
  endTime?: unknown;
}
interface CombinationPinkDto extends Omit<ICombinationPink, 'status' | 'expireTime'> {
  status?: string;
  expireTime?: unknown;
}

const toCoupon = (d: CouponManagerDto): ICoupon => ({
  ...d,
  id: d.couponId,
  type: codeOf(COUPON_TYPE, d.type),
  status: codeOf(COUPON_STATUS, d.status),
  goodsType: codeOf(COUPON_GOODS_TYPE, d.goodsType),
  timeType: codeOf(COUPON_TIME_TYPE, d.timeType),
  startTime: fromServerDateTime(d.startTime),
  endTime: fromServerDateTime(d.endTime),
});

const toCouponUser = (d: UserCouponDto): ICouponUser => ({
  ...d,
  id: d.userCouponId,
  status: codeOf(USER_COUPON_STATUS, d.status),
  startTime: fromServerDateTime(d.startTime),
  endTime: fromServerDateTime(d.endTime),
  usedTime: fromServerDateTime(d.usedTime),
});

const toCombination = (d: CombinationManagerDto): ICombination => ({
  ...d,
  id: d.combinationId,
  status: codeOf(COMBINATION_STATUS, d.status),
  startTime: fromServerDateTime(d.startTime),
  endTime: fromServerDateTime(d.endTime),
});

const toPink = (d: CombinationPinkDto): ICombinationPink => ({
  ...d,
  status: codeOf(PINK_STATUS, d.status),
  expireTime: fromServerDateTime(d.expireTime),
});

// Promotion's core JacksonConfig only accepts strict ISO-8601 LocalDateTime
// ('2026-07-10T00:00:00'); pad the 'YYYY-MM-DDTHH:mm' that datetime-local
// inputs produce, and drop empty strings.
const isoDateTime = (v?: string): string | undefined => {
  if (!v) return undefined;
  return v.length === 16 ? `${v}:00` : v;
};

const couponCommand = (c: ICoupon) =>
  clean({
    name: c.name,
    description: c.description,
    tag: c.tag,
    total: c.total,
    discount: c.discount,
    min: c.min,
    limitPerUser: c.limitPerUser,
    type: c.type,
    status: c.status,
    goodsType: c.goodsType,
    goodsValue: c.goodsValue,
    code: c.code,
    timeType: c.timeType,
    days: c.days,
    startTime: isoDateTime(c.startTime),
    endTime: isoDateTime(c.endTime),
  });

const combinationCommand = (c: ICombination) =>
  clean({
    goodsId: c.goodsId,
    title: c.title,
    picUrl: c.picUrl,
    combinationPrice: c.combinationPrice,
    originalPrice: c.originalPrice,
    requiredMembers: c.requiredMembers,
    limitPerUser: c.limitPerUser,
    startTime: isoDateTime(c.startTime),
    endTime: isoDateTime(c.endTime),
  });

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
  tagTypes: ['Ad', 'Coupon', 'Combination'],
  endpoints: builder => ({
    // ----- Ad (edge-hosted, legacy envelope) ------------------------------
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

    // ----- Coupon (promotion-service) -------------------------------------
    listCoupons: builder.query<BareList<ICoupon>, PageParams>({
      query: ({ page, limit }) => ({ url: '/promotion/coupon/list', params: { page, limit } }),
      transformResponse: (r: CouponManagerDto[]) => ({ list: (r ?? []).map(toCoupon) }),
      providesTags: ['Coupon'],
    }),
    readCoupon: builder.query<ICoupon, number | string>({
      query: id => ({ url: `/promotion/coupon/${id}` }),
      transformResponse: (r: CouponManagerDto) => toCoupon(r ?? {}),
    }),
    listCouponUsers: builder.query<BareList<ICouponUser>, PageParams & { couponId: number | string }>({
      query: ({ couponId, page, limit }) => ({ url: `/promotion/coupon/${couponId}/users`, params: { page, limit } }),
      transformResponse: (r: UserCouponDto[]) => ({ list: (r ?? []).map(toCouponUser) }),
      providesTags: ['Coupon'],
    }),
    createCoupon: builder.mutation<PromotionOperation, ICoupon>({
      query: body => ({ url: '/promotion/coupon', method: 'POST', body: couponCommand(body) }),
      invalidatesTags: ['Coupon'],
    }),
    updateCoupon: builder.mutation<PromotionOperation, ICoupon>({
      query: body => ({ url: `/promotion/coupon/${body.id}`, method: 'PUT', body: couponCommand(body) }),
      invalidatesTags: ['Coupon'],
    }),
    deleteCoupon: builder.mutation<PromotionOperation, ICoupon>({
      query: body => ({ url: `/promotion/coupon/${body.id}`, method: 'DELETE' }),
      invalidatesTags: ['Coupon'],
    }),
    grantCoupon: builder.mutation<PromotionOperation, { couponId: number; userId: number }>({
      query: body => ({ url: '/promotion/coupon/grant', method: 'POST', body }),
      invalidatesTags: ['Coupon'],
    }),

    // ----- Combination / group-buy (promotion-service) ---------------------
    listCombinations: builder.query<BareList<ICombination>, void>({
      query: () => ({ url: '/promotion/combination/list' }),
      transformResponse: (r: CombinationManagerDto[]) => ({ list: (r ?? []).map(toCombination) }),
      providesTags: ['Combination'],
    }),
    readCombination: builder.query<ICombination, number | string>({
      query: id => ({ url: `/promotion/combination/${id}` }),
      transformResponse: (r: CombinationManagerDto) => toCombination(r ?? {}),
    }),
    createCombination: builder.mutation<PromotionOperation, ICombination>({
      query: body => ({ url: '/promotion/combination', method: 'POST', body: combinationCommand(body) }),
      invalidatesTags: ['Combination'],
    }),
    updateCombination: builder.mutation<PromotionOperation, ICombination>({
      query: body => ({ url: `/promotion/combination/${body.id}`, method: 'PUT', body: combinationCommand(body) }),
      invalidatesTags: ['Combination'],
    }),
    deleteCombination: builder.mutation<PromotionOperation, ICombination>({
      query: body => ({ url: `/promotion/combination/${body.id}`, method: 'DELETE' }),
      invalidatesTags: ['Combination'],
    }),
    activateCombination: builder.mutation<PromotionOperation, number>({
      query: id => ({ url: `/promotion/combination/${id}/activate`, method: 'POST' }),
      invalidatesTags: ['Combination'],
    }),
    expireCombination: builder.mutation<PromotionOperation, number>({
      query: id => ({ url: `/promotion/combination/${id}/expire`, method: 'POST' }),
      invalidatesTags: ['Combination'],
    }),
    listPinks: builder.query<BareList<ICombinationPink>, { combinationId?: number | string; status?: number }>({
      query: ({ combinationId, status }) => ({
        url: combinationId != null ? `/promotion/combination/${combinationId}/pinks` : '/promotion/combination/pinks',
        params: clean({ status }),
      }),
      transformResponse: (r: CombinationPinkDto[]) => ({ list: (r ?? []).map(toPink) }),
      providesTags: ['Combination'],
    }),
  }),
});

// Normalise an RTK-Query mutation result against the promotion operation
// shape into an error message, or null on success. HTTP 400 rejects into
// `error` with the same body; transport errors have no body.
export const promotionOpMessage = (res: unknown): string | null => {
  const r = res as { data?: PromotionOperation; error?: { status?: number | string; data?: PromotionOperation } };
  if (r && 'error' in r && r.error) {
    return r.error.data?.message || `Request failed (${r.error.status ?? 'network'})`;
  }
  if (r && 'data' in r && r.data) {
    return r.data.success ? null : r.data.message || 'Request failed.';
  }
  return 'Request failed.';
};

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
  useGrantCouponMutation,
  useListCombinationsQuery,
  useReadCombinationQuery,
  useCreateCombinationMutation,
  useUpdateCombinationMutation,
  useDeleteCombinationMutation,
  useActivateCombinationMutation,
  useExpireCombinationMutation,
  useListPinksQuery,
} = adminPromotionApi;
