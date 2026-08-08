import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { getAdminToken } from 'app/shared/reducers/admin-auth';
import { fromServerDateTime } from 'app/shared/util/server-datetime';

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
  // Wave 14: the daily tick auto-approves top candidates; the contract leaves
  // the exact actor field open, so keep the row open for detection.
  [key: string]: unknown;
}

// Wave 14: a candidate counts as auto-approved only on a POSITIVE signal —
// either an explicit `auto` flag, or an actor-ish field that is present but
// empty/'auto'/'system' ("approved without a manual actor"). Rows without any
// such key render no badge: never claim "auto" on a guess.
export const isAutoApprovedCandidate = (c: IDealCandidate): boolean => {
  if ((c.status || '').toLowerCase() !== 'approved') return false;
  if (c.auto === true) return true;
  for (const key of ['approvedBy', 'actor', 'decidedBy']) {
    if (key in c) {
      const v = c[key];
      if (v == null || v === '') return true;
      return typeof v === 'string' && ['auto', 'system'].includes(v.toLowerCase());
    }
  }
  return false;
};

// ---- Wave 14: inventory governance (retirement, arrivals, margin tuning) ----

export type RetireStatus = 'proposed' | 'approved' | 'dismissed' | 'executed';

export interface IRetireCandidate {
  goodsId: number;
  name?: string;
  picUrl?: string;
  categoryId?: number;
  cost: number | null;
  retailPrice?: number;
  marginPct?: number | null;
  stockTotal?: number;
  unavailableDays?: number;
  views?: number;
  salesQty?: number;
  score?: number;
  reasons?: string[];
  status?: string;
  executeOn?: string;
}

export interface ApproveRetireCommand {
  goodsIds: number[];
  /** 'YYYY-MM-DD'; the backend defaults to the next scheduled day when absent. */
  executeOn?: string;
}

export interface IArrivalsCategory {
  categoryId: number;
  name?: string;
  arrivals?: number;
  avgMarginPct?: number | null;
  avgRetailPrice?: number | null;
  dealScore?: number | null;
}

export interface IArrivalsInsight {
  since?: string;
  runs?: number;
  categories: IArrivalsCategory[];
}

export interface IMarginSimulation {
  currentMargin?: number;
  simulatedMargin?: number;
  goodsCount?: number;
  avgPriceNow?: number | null;
  avgPriceAt?: number | null;
  potentialProfitNow?: number | null;
  potentialProfitAt?: number | null;
}

export interface IMarginOverride {
  categoryId: number;
  margin?: number;
  name?: string;
  updateTime?: string;
  [key: string]: unknown;
}

export interface ApproveDealCommand {
  goodsId: number;
  dealPrice: number;
  startTime: string;
  stopTime: string;
  stock: number;
}

// ---- Wave 19: promo candidates (coupon/groupon suggestions) ----------------

export type PromoKind = 'coupon' | 'groupon';

// The nightly scorer's concrete proposal, PRE-VALIDATED against the Wave-18
// margin guard server-side. Coupon and groupon suggestions share the field
// space (all optional) — the row's `kind` says which half is populated.
export interface IPromoSuggestion {
  // coupon
  scopeType?: 'category' | 'goods';
  categoryId?: number;
  goodsIds?: number[];
  discountType?: number; // 0 flat, 1 percent
  discount?: number;
  discountCap?: number | null;
  minAmount?: number;
  maxDiscount?: number;
  // groupon
  combinationPrice?: number;
  originalPrice?: number;
  requiredMembers?: number;
  limitPerUser?: number;
  windowDays?: number;
}

export interface IPromoCandidate {
  id?: number;
  goodsId: number;
  name?: string;
  picUrl?: string;
  /** Resolved to the L1 ROOT by the backend. */
  categoryId?: number;
  kind?: PromoKind;
  day?: string;
  tier?: string;
  score?: number;
  cost: number | null;
  retailPrice?: number;
  marginPct?: number | null;
  stockTotal?: number;
  rating?: number;
  reviewCount?: number;
  suggestion?: IPromoSuggestion | null;
  reasons?: string[];
  status?: string;
  /** Created coupon/combination id once the row was consumed. */
  refId?: number | null;
}

export interface PromoCandidateListResponse {
  /** The served day ('YYYY-MM-DD') — the latest scored day when none was asked for. */
  day?: string | null;
  list: IPromoCandidate[];
}

export interface PromoDecisionCommand {
  goodsId: number;
  kind: PromoKind;
  day?: string;
  /** consume only: the created coupon/combination id. */
  refId?: number;
}

interface ApiEnvelope<T> {
  errno: number;
  errmsg: string;
  data?: T;
}

// ---- served-shape normalisation --------------------------------------------
// The goods-management insight surface (verified against master 68ebcbb38)
// deviates from the raw contract sketch in serialization only: list rows key
// the goods as `goodsId`, and the module's core Jackson setup serializes
// LocalDate/LocalDateTime as numeric arrays ([y,m,d,...]) and the candidate
// `day` as epoch millis. Normalise here so views keep plain string/number
// rendering.

type Raw = Record<string, unknown>;

// day: LocalDate array | epoch millis | ISO string → 'YYYY-MM-DD'
const toDay = (v: unknown): string | undefined => {
  if (typeof v === 'number') return new Date(v).toISOString().slice(0, 10);
  return fromServerDateTime(v)?.slice(0, 10);
};

const toGoodsRow = (r: Raw): IInsightGoodsRow =>
  ({
    ...r,
    id: (r.id ?? r.goodsId) as number,
    arrivalDate: fromServerDateTime(r.arrivalDate),
  }) as IInsightGoodsRow;

const toSeriesPoint = (r: Raw): IInsightSeriesPoint => ({ ...r, day: toDay(r.day) ?? '' }) as IInsightSeriesPoint;

const toDeal = (r: Raw): IInsightDeal =>
  ({
    ...r,
    startTime: fromServerDateTime(r.startTime),
    stopTime: fromServerDateTime(r.stopTime),
  }) as IInsightDeal;

const toCandidate = (r: Raw): IDealCandidate => ({ ...r, day: toDay(r.day) }) as IDealCandidate;

// Row `day` is a DATE column (array/millis serialization hazard); `suggestion`
// and `reasons` arrive already parsed to JSON values by the backend.
const toPromoCandidate = (r: Raw): IPromoCandidate => ({ ...r, day: toDay(r.day) }) as IPromoCandidate;

// executeOn is a DATE column — same array/millis serialization hazard as day.
const toRetireCandidate = (r: Raw): IRetireCandidate => ({ ...r, executeOn: toDay(r.executeOn) }) as IRetireCandidate;

// The contract says only "GET /margin-overrides → list" — accept both a bare
// array and a {list} wrapper.
const toOverrides = (d: unknown): IMarginOverride[] => {
  const rows = Array.isArray(d) ? d : ((d as { list?: unknown[] })?.list ?? []);
  return rows.map(r => ({ ...(r as Raw), updateTime: fromServerDateTime((r as Raw).updateTime) }) as IMarginOverride);
};

const toDetail = (d?: IInsightGoodsDetail): IInsightGoodsDetail | undefined =>
  d && {
    ...d,
    variants: d.variants ?? [],
    series: (d.series ?? []).map(p => toSeriesPoint(p as unknown as Raw)),
    totals: d.totals ?? {},
    deals: (d.deals ?? []).map(x => toDeal(x as unknown as Raw)),
  };

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
  tagTypes: ['Categories', 'InsightGoods', 'Candidates', 'RetireCandidates', 'MarginOverrides', 'PromoCandidates'],
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
      transformResponse: (r: ApiEnvelope<InsightGoodsListResponse>) => {
        const d = r?.data ?? { total: 0, pages: 0, limit: 0, page: 1, list: [] };
        return { ...d, list: (d.list ?? []).map(row => toGoodsRow(row as unknown as Raw)) };
      },
      providesTags: ['InsightGoods'],
    }),
    getInsightGoodsDetail: builder.query<IInsightGoodsDetail | undefined, number | string>({
      query: id => ({ url: `/goods/${id}` }),
      transformResponse: (r: ApiEnvelope<IInsightGoodsDetail>) => toDetail(r?.data),
      providesTags: (result, err, id) => [{ type: 'InsightGoods', id }],
    }),
    getDealCandidates: builder.query<{ list: IDealCandidate[] }, { day?: string }>({
      query: ({ day }) => ({ url: '/deal-candidates', params: day ? { day } : undefined }),
      transformResponse: (r: ApiEnvelope<{ list: IDealCandidate[] }>) => ({
        list: (r?.data?.list ?? []).map(c => toCandidate(c as unknown as Raw)),
      }),
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
    // ---- Wave 19: promo candidates ----------------------------------------
    // `day` defaults server-side to the latest scored day for the kind.
    getPromoCandidates: builder.query<PromoCandidateListResponse, { kind: PromoKind; day?: string; status?: string }>({
      query: ({ kind, day, status }) => ({
        url: '/promo-candidates',
        params: { kind, ...(day ? { day } : {}), ...(status ? { status } : {}) },
      }),
      transformResponse: (r: ApiEnvelope<PromoCandidateListResponse>) => ({
        day: typeof r?.data?.day === 'string' ? r.data.day : toDay(r?.data?.day),
        list: (r?.data?.list ?? []).map(c => toPromoCandidate(c as unknown as Raw)),
      }),
      providesTags: ['PromoCandidates'],
    }),
    // Dismiss/consume CAS from `proposed` (errno 653 on a lost race); consume
    // is fired by the coupon/groupon forms AFTER a successful create and is
    // FAIL-SOFT there — a failed consume never blocks or rolls back the create.
    dismissPromoCandidate: builder.mutation<ApiEnvelope<unknown>, PromoDecisionCommand>({
      query: ({ goodsId, kind, day }) => ({
        url: `/promo-candidates/${goodsId}/dismiss`,
        method: 'POST',
        body: { kind, ...(day ? { day } : {}) },
      }),
      invalidatesTags: ['PromoCandidates'],
    }),
    consumePromoCandidate: builder.mutation<ApiEnvelope<unknown>, PromoDecisionCommand>({
      query: ({ goodsId, kind, day, refId }) => ({
        url: `/promo-candidates/${goodsId}/consume`,
        method: 'POST',
        body: { kind, ...(day ? { day } : {}), ...(refId != null ? { refId } : {}) },
      }),
      invalidatesTags: ['PromoCandidates'],
    }),
    // Manual trigger of the nightly scoring (admin re-run / dev acceptance).
    runPromoCandidates: builder.mutation<ApiEnvelope<unknown>, { day?: string }>({
      query: ({ day }) => ({ url: '/promo-candidates/run', method: 'POST', params: day ? { day } : undefined }),
      invalidatesTags: ['PromoCandidates'],
    }),
    // ---- Wave 14: retirement pipeline -------------------------------------
    getRetireCandidates: builder.query<{ list: IRetireCandidate[] }, { status: RetireStatus }>({
      query: ({ status }) => ({ url: '/retire-candidates', params: { status } }),
      transformResponse: (r: ApiEnvelope<{ list: IRetireCandidate[] }>) => ({
        list: (r?.data?.list ?? []).map(c => toRetireCandidate(c as unknown as Raw)),
      }),
      providesTags: ['RetireCandidates'],
    }),
    // Approve/dismiss CAS from `proposed` (errno 653 on a lost race); the tag
    // invalidation refetches either way, so a stale row disappears on 653 too.
    approveRetireCandidates: builder.mutation<ApiEnvelope<unknown>, ApproveRetireCommand>({
      query: body => ({ url: '/retire-candidates/approve', method: 'POST', body }),
      invalidatesTags: ['RetireCandidates'],
    }),
    dismissRetireCandidate: builder.mutation<ApiEnvelope<unknown>, { goodsId: number }>({
      query: ({ goodsId }) => ({ url: `/retire-candidates/${goodsId}/dismiss`, method: 'POST' }),
      invalidatesTags: ['RetireCandidates'],
    }),
    // ---- Wave 14: arrivals windows ----------------------------------------
    getArrivals: builder.query<IArrivalsInsight, { runs: 1 | 2 }>({
      query: ({ runs }) => ({ url: '/arrivals', params: { runs } }),
      transformResponse: (r: ApiEnvelope<IArrivalsInsight>) => {
        const d = r?.data ?? { categories: [] };
        return { ...d, since: fromServerDateTime(d.since), categories: d.categories ?? [] };
      },
    }),
    // ---- Wave 14: per-category margin tuning ------------------------------
    // Pure read — no price mutation; costed goods only.
    simulateCategoryMargin: builder.query<IMarginSimulation | undefined, { categoryId: number; margin: number }>({
      query: ({ categoryId, margin }) => ({ url: `/categories/${categoryId}/simulate`, params: { margin } }),
      transformResponse: (r: ApiEnvelope<IMarginSimulation>) => r?.data,
    }),
    getMarginOverrides: builder.query<IMarginOverride[], void>({
      query: () => ({ url: '/margin-overrides' }),
      transformResponse: (r: ApiEnvelope<unknown>) => toOverrides(r?.data),
      providesTags: ['MarginOverrides'],
    }),
    putCategoryMargin: builder.mutation<ApiEnvelope<unknown>, { categoryId: number; margin: number }>({
      query: ({ categoryId, margin }) => ({ url: `/categories/${categoryId}/margin`, method: 'PUT', body: { margin } }),
      invalidatesTags: ['MarginOverrides'],
    }),
    deleteCategoryMargin: builder.mutation<ApiEnvelope<unknown>, { categoryId: number }>({
      query: ({ categoryId }) => ({ url: `/categories/${categoryId}/margin`, method: 'DELETE' }),
      invalidatesTags: ['MarginOverrides'],
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
  useGetPromoCandidatesQuery,
  useDismissPromoCandidateMutation,
  useConsumePromoCandidateMutation,
  useRunPromoCandidatesMutation,
  useGetRetireCandidatesQuery,
  useApproveRetireCandidatesMutation,
  useDismissRetireCandidateMutation,
  useGetArrivalsQuery,
  useSimulateCategoryMarginQuery,
  useGetMarginOverridesQuery,
  usePutCategoryMarginMutation,
  useDeleteCategoryMarginMutation,
} = insightApi;
