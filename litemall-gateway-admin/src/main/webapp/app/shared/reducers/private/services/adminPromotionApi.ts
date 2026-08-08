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
export const COUPON_DISCOUNT_TYPE = { 0: 'Flat', 1: 'Percent' };

// Wave 18: discount_type may arrive as the numeric code or an enum display
// name; rows predating V51 (or a not-yet-upgraded promotion-service) carry
// no field at all — those are flat coupons by construction.
export const toDiscountTypeCode = (v?: string | number | null): number => {
  if (v == null) return 0;
  if (typeof v === 'number') return v;
  return v.trim().toLowerCase() === 'percent' ? 1 : 0;
};
export const COUPON_TIME_TYPE = { 0: 'Relative days', 1: 'Absolute window' };
export const USER_COUPON_STATUS = { 0: 'Usable', 1: 'Used', 2: 'Expired', 3: 'Withdrawn' };
export const COMBINATION_STATUS = { 0: 'Draft', 1: 'Active', 2: 'Expired', 3: 'Offline' };
export const PINK_STATUS = { 0: 'Pending', 1: 'Success', 2: 'Failed' };

// Promotion-service manager DTO wire shapes (before normalisation). Datetime
// fields arrive as ISO strings OR LocalDateTime arrays (fromServerDateTime).
interface CouponManagerDto extends Omit<ICoupon, 'id' | 'type' | 'status' | 'goodsType' | 'discountType' | 'timeType' | 'startTime' | 'endTime'> {
  couponId?: number;
  type?: string;
  status?: string;
  goodsType?: string;
  discountType?: string | number;
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

export const toCoupon = (d: CouponManagerDto): ICoupon => ({
  ...d,
  id: d.couponId,
  type: codeOf(COUPON_TYPE, d.type),
  status: codeOf(COUPON_STATUS, d.status),
  goodsType: codeOf(COUPON_GOODS_TYPE, d.goodsType),
  discountType: toDiscountTypeCode(d.discountType),
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

// ----- Campaign (promotion-service Phase 2, admin-only) ---------------------
// CampaignManagerDtoResponse from /srv/private/admin/promotion/campaign:
// status arrives as the enum display name (kept as a string), datetimes as
// ISO strings or LocalDateTime arrays.
export interface ICampaign {
  id?: number;
  name?: string;
  targetSegments?: string[];
  minRecencyScore?: number;
  minFrequencyScore?: number;
  minMonetaryScore?: number;
  targetGoodsIds?: number[];
  linkedPromotionType?: string;
  linkedPromotionId?: number;
  startTime?: string;
  endTime?: string;
  maxAudience?: number;
  maxSpend?: number;
  assignedCount?: number;
  spentBudget?: number;
  status?: string;
}

interface CampaignManagerDto extends Omit<ICampaign, 'id' | 'startTime' | 'endTime'> {
  campaignId?: number;
  startTime?: unknown;
  endTime?: unknown;
}

const toCampaign = (d: CampaignManagerDto): ICampaign => ({
  ...d,
  id: d.campaignId,
  startTime: fromServerDateTime(d.startTime),
  endTime: fromServerDateTime(d.endTime),
});

// Wave 12: category campaign composer (Wave-12 CONTRACT). The response
// carries the created campaign id, the per-platform social_post draft ids
// and an HONEST per-platform status (disabled/failed while Meta/TikTok
// tokens are absent) — the dialog renders those verbatim, never a fake
// success.
export interface ICampaignPlatformStatus {
  platform?: string;
  status?: string;
  postId?: number;
  message?: string;
  reason?: string;
}

export interface ICampaignFromCategoryResult {
  campaignId?: number;
  postIds?: number[];
  platforms?: ICampaignPlatformStatus[];
  message?: string;
}

export interface CampaignFromCategoryCommand {
  categoryL1Id: number;
  name?: string;
  schedule: { start: string; stop: string };
  platforms: string[];
  goodsIds?: number[];
}

// The mutation rides the promotion-service operation shape; the payload may
// arrive either bare or inside PromotionOperation.data — normalise both.
const toFromCategoryResult = (r: unknown): ICampaignFromCategoryResult => {
  const op = r as PromotionOperation & ICampaignFromCategoryResult;
  const data = (op?.data ?? {}) as ICampaignFromCategoryResult;
  if (data.campaignId != null || data.platforms || data.postIds) return { ...data, message: op?.message };
  return { campaignId: op?.campaignId, postIds: op?.postIds, platforms: op?.platforms, message: op?.message };
};

// Promotion's core JacksonConfig only accepts strict ISO-8601 LocalDateTime
// ('2026-07-10T00:00:00'); pad the 'YYYY-MM-DDTHH:mm' that datetime-local
// inputs produce, and drop empty strings.
const isoDateTime = (v?: string): string | undefined => {
  if (!v) return undefined;
  return v.length === 16 ? `${v}:00` : v;
};

// ----- Wave 22: RFM-targeted coupon delivery + measurement ------------------
// POST /promotion/coupon/{couponId}/deliver — preview:true computes the
// matching user set only (ZERO side effects); the real run grants through the
// existing coupon grant path (idempotent per user via the claim limit).
// Typed refusals (expired/inactive coupon) ride the PromotionOperation
// message and are surfaced VERBATIM via promotionOpMessage.

export interface CouponSegment {
  /** "bought within N days" */
  recencyDays?: number;
  /** "at least N paid orders" */
  minFrequency?: number;
  /** "spent at least $N" */
  minMonetary?: number;
}

export interface CouponDeliverCommand extends CouponSegment {
  couponId: number;
  preview?: boolean;
}

export interface ICouponDeliverResult {
  matched?: number;
  granted?: number;
  skipped?: number;
}

export interface ICouponPerformance {
  granted?: number;
  used?: number;
  redemptionPct?: number | null;
  ordersCount?: number;
  revenue?: number;
  avgOrderValue?: number | null;
}

export interface ICouponDelivery {
  id?: number;
  couponId?: number;
  /** The delivered segment as stored — a JSON string or an already-parsed object. */
  segmentJson?: string | Record<string, unknown>;
  matched?: number;
  granted?: number;
  skipped?: number;
  addTime?: string;
}

// Body builder — preview travels only when true (a real run simply omits it).
export const deliverCommand = ({ couponId, preview, ...segment }: CouponDeliverCommand): Record<string, unknown> =>
  clean({ recencyDays: segment.recencyDays, minFrequency: segment.minFrequency, minMonetary: segment.minMonetary, ...(preview ? { preview: true } : {}) });

// The counts may arrive bare or inside PromotionOperation.data — accept both.
export const toDeliverResult = (r: unknown): ICouponDeliverResult => {
  const op = r as (PromotionOperation & ICouponDeliverResult) | undefined;
  const data = (op?.data ?? {}) as ICouponDeliverResult;
  if (data.matched != null || data.granted != null || data.skipped != null) return { matched: data.matched, granted: data.granted, skipped: data.skipped };
  return { matched: op?.matched, granted: op?.granted, skipped: op?.skipped };
};

// Performance read — bare DTO per the REST-shaped promotion admin surface, but
// tolerate an envelope/operation wrapper.
export const toCouponPerformance = (r: unknown): ICouponPerformance => {
  const raw = (r ?? {}) as Record<string, unknown>;
  const src = (raw.granted != null || raw.used != null || raw.revenue != null ? raw : (raw.data as Record<string, unknown>)) ?? {};
  const num = (v: unknown): number | undefined => (v == null ? undefined : Number(v));
  return {
    granted: num(src.granted),
    used: num(src.used),
    redemptionPct: src.redemptionPct == null ? null : Number(src.redemptionPct),
    ordersCount: num(src.ordersCount),
    revenue: num(src.revenue),
    avgOrderValue: src.avgOrderValue == null ? null : Number(src.avgOrderValue),
  };
};

interface CouponDeliveryDto extends Omit<ICouponDelivery, 'id' | 'addTime'> {
  deliveryId?: number;
  id?: number;
  addTime?: unknown;
}

const toDelivery = (d: CouponDeliveryDto): ICouponDelivery => ({
  ...d,
  id: d.id ?? d.deliveryId,
  addTime: fromServerDateTime(d.addTime),
});

// Contract: page envelope {list,total,...}; accept a bare array defensively
// (the older promotion list endpoints return bare arrays).
export const toDeliveriesPage = (r: unknown): PagedList<ICouponDelivery> => {
  if (Array.isArray(r)) {
    const list = r.map(toDelivery);
    return { list, total: list.length, page: 1, limit: list.length, pages: 1 };
  }
  const page = (r ?? {}) as Partial<PagedList<CouponDeliveryDto>>;
  return {
    list: (page.list ?? []).map(toDelivery),
    total: page.total ?? 0,
    page: page.page ?? 1,
    limit: page.limit ?? 0,
    pages: page.pages ?? 0,
  };
};

// Wave 18: discountType always travels (0 survives `clean`); the $-cap only
// makes sense for percent coupons — never send a stale cap with a flat one.
export const couponCommand = (c: ICoupon) =>
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
    discountType: c.discountType ?? 0,
    discountCap: (c.discountType ?? 0) === 1 ? c.discountCap : undefined,
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
  tagTypes: ['Ad', 'Coupon', 'Combination', 'Campaign', 'CouponDelivery', 'CouponPerformance'],
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
      invalidatesTags: ['Coupon', 'CouponPerformance'],
    }),

    // ----- Wave 22: RFM-targeted delivery + measurement --------------------
    // preview:true is a pure count — it must not invalidate anything.
    deliverCoupon: builder.mutation<PromotionOperation, CouponDeliverCommand>({
      query: cmd => ({ url: `/promotion/coupon/${cmd.couponId}/deliver`, method: 'POST', body: deliverCommand(cmd) }),
      invalidatesTags: (result, err, { preview }) => (preview ? [] : ['Coupon', 'CouponDelivery', 'CouponPerformance']),
    }),
    getCouponPerformance: builder.query<ICouponPerformance, number | string>({
      query: couponId => ({ url: `/promotion/coupon/${couponId}/performance` }),
      transformResponse: toCouponPerformance,
      providesTags: ['CouponPerformance'],
    }),
    listCouponDeliveries: builder.query<PagedList<ICouponDelivery>, PageParams & { couponId: number | string }>({
      query: ({ couponId, page, limit }) => ({ url: '/promotion/coupon/deliveries', params: { couponId, page, limit } }),
      transformResponse: toDeliveriesPage,
      providesTags: ['CouponDelivery'],
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
    // ----- Campaign (promotion-service Phase 2) ----------------------------
    listCampaigns: builder.query<BareList<ICampaign>, void>({
      query: () => ({ url: '/promotion/campaign/list' }),
      transformResponse: (r: CampaignManagerDto[]) => ({ list: (r ?? []).map(toCampaign) }),
      providesTags: ['Campaign'],
    }),
    activateCampaign: builder.mutation<PromotionOperation, number>({
      query: id => ({ url: `/promotion/campaign/${id}/activate`, method: 'POST' }),
      invalidatesTags: ['Campaign'],
    }),
    evaluateCampaign: builder.mutation<PromotionOperation, number>({
      query: id => ({ url: `/promotion/campaign/${id}/evaluate`, method: 'POST' }),
      invalidatesTags: ['Campaign'],
    }),
    // Wave 12: create a scheduled category campaign + per-platform drafts.
    createCampaignFromCategory: builder.mutation<ICampaignFromCategoryResult, CampaignFromCategoryCommand>({
      query: ({ schedule, ...rest }) => ({
        url: '/promotion/campaign/from-category',
        method: 'POST',
        body: clean({
          ...rest,
          schedule: { start: isoDateTime(schedule.start), stop: isoDateTime(schedule.stop) },
        }),
      }),
      transformResponse: toFromCategoryResult,
      invalidatesTags: ['Campaign'],
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

// Wave 18: on a SUCCESSFUL coupon save the margin guard rides a non-blocking
// `uncostedCount` along in the operation payload (goods in the coupon's scope
// whose CJ cost is not captured yet, so the guard could not evaluate them).
// Rejections themselves flow through promotionOpMessage VERBATIM — they state
// the computed maximum discount/rate.
export const promotionOpWarning = (res: unknown): string | null => {
  const r = res as { data?: PromotionOperation };
  const op = r && 'data' in r ? r.data : undefined;
  if (!op?.success) return null;
  const n = Number((op.data as Record<string, unknown> | undefined)?.uncostedCount);
  if (Number.isFinite(n) && n > 0) {
    return `${n} item${n === 1 ? '' : 's'} in this coupon's scope ${n === 1 ? 'has' : 'have'} no captured cost yet — the margin guard could not evaluate ${n === 1 ? 'it' : 'them'}.`;
  }
  return null;
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
  useDeliverCouponMutation,
  useGetCouponPerformanceQuery,
  useListCouponDeliveriesQuery,
  useListCombinationsQuery,
  useReadCombinationQuery,
  useCreateCombinationMutation,
  useUpdateCombinationMutation,
  useDeleteCombinationMutation,
  useActivateCombinationMutation,
  useExpireCombinationMutation,
  useListPinksQuery,
  useListCampaignsQuery,
  useActivateCampaignMutation,
  useEvaluateCampaignMutation,
  useCreateCampaignFromCategoryMutation,
} = adminPromotionApi;
