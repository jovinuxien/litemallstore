import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { getAdminToken } from 'app/shared/reducers/admin-auth';
import { fromServerDateTime } from 'app/shared/util/server-datetime';

// RTK Query client for the affiliate portal (Wave 5), served by
// litemall-order through this gateway under '/srv/private/affiliate/**' as an
// authenticated ROLE_AFFILIATE session. Identity is the forwarded X-User-Id —
// every endpoint is self-scoped, no id params anywhere. Contract per the
// Wave-5 spec (CLAUDE.md order block item 7 / order's
// docs/handoff-affiliate-portal.md once committed); shapes are read
// defensively until the handoff lands. Legacy envelope {errno,errmsg,data} —
// HTTP 200 even on business errors (errno 660-family), so mutations return
// the raw envelope for the views to inspect.

export interface ApiEnvelope<T = unknown> {
  errno: number;
  errmsg: string;
  data?: T;
}

export interface PagedList<T> {
  list: T[];
  total: number;
  page: number;
  limit: number;
  pages: number;
}

export interface AffiliateDashboard {
  /** litemall_user.brokerage_price — withdrawable now. */
  available?: number;
  frozenSum?: number;
  lifetimeEarned?: number;
  thisMonth?: number;
  spreadCount?: number;
  referredOrders?: number;
}

/** litemall_user_brokerage_record row. status: 0 frozen, 1 valid, -1 invalid. pm: 1 credit, 0 debit. */
export interface IBrokerageRecord {
  id?: number;
  linkId?: string;
  linkType?: string;
  pm?: number;
  title?: string;
  price?: number;
  balance?: number;
  mark?: string;
  status?: number;
  freezeTime?: string;
  unfreezeTime?: string;
  addTime?: string;
}

export interface ITeamMember {
  /** Masked server-side. */
  nickname?: string;
  addTime?: string;
  payCount?: number;
}

export interface AffiliateLinks {
  inviteCode?: string;
  registerUrl?: string;
  /** Canonical product deep-link, with a goods-id placeholder. */
  productUrl?: string;
  productUrlTemplate?: string;
}

/** Mirrors order's LitemallExtractRequestCommand + the Wave-5 `source` field. */
export interface ExtractRequest {
  extractAmount: number;
  realName: string;
  extractType: string;
  bankCode?: string;
  bankAddress?: string;
  source: 'brokerage';
}

/** litemall_user_extract row. status: 0 pending, 1 processing, 2 completed, -1 rejected. */
export interface IExtractRow {
  id?: number;
  realName?: string;
  extractType?: string;
  bankCode?: string;
  bankAddress?: string;
  extractPrice?: number;
  balance?: number;
  status?: number;
  failMsg?: string;
  failTime?: string;
  addTime?: string;
}

const emptyPage = <T>(): PagedList<T> => ({ list: [], total: 0, page: 1, limit: 0, pages: 0 });

// Some rows serialize LocalDateTime as a numeric array — normalise to ISO.
const isoTimes = <T extends { addTime?: unknown; freezeTime?: unknown; unfreezeTime?: unknown; failTime?: unknown }>(
  page: PagedList<T>
): PagedList<T> => ({
  ...page,
  list: (page.list ?? []).map(row => ({
    ...row,
    addTime: fromServerDateTime(row.addTime),
    freezeTime: fromServerDateTime(row.freezeTime),
    unfreezeTime: fromServerDateTime(row.unfreezeTime),
    failTime: fromServerDateTime(row.failTime),
  })),
});

export const affiliateApi = createApi({
  reducerPath: 'affiliateApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/affiliate',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      return headers;
    },
  }),
  tagTypes: ['Dashboard', 'Records', 'Extracts'],
  endpoints: builder => ({
    getDashboard: builder.query<AffiliateDashboard, void>({
      query: () => ({ url: '/dashboard' }),
      transformResponse: (r: ApiEnvelope<AffiliateDashboard>) => r?.data ?? {},
      providesTags: ['Dashboard'],
    }),
    listRecords: builder.query<PagedList<IBrokerageRecord>, { page: number; limit: number }>({
      query: params => ({ url: '/records', params }),
      transformResponse: (r: ApiEnvelope<PagedList<IBrokerageRecord>>) => isoTimes(r?.data ?? emptyPage<IBrokerageRecord>()),
      providesTags: ['Records'],
    }),
    listTeam: builder.query<PagedList<ITeamMember>, { page: number; limit: number }>({
      query: params => ({ url: '/team', params }),
      transformResponse: (r: ApiEnvelope<PagedList<ITeamMember>>) => isoTimes(r?.data ?? emptyPage<ITeamMember>()),
    }),
    getLinks: builder.query<AffiliateLinks, void>({
      query: () => ({ url: '/links' }),
      transformResponse: (r: ApiEnvelope<AffiliateLinks>) => r?.data ?? {},
    }),
    // Below-min / insufficient balance come back as 422-family business
    // errors — raw envelope on purpose so the form can show errmsg inline.
    requestExtract: builder.mutation<ApiEnvelope, ExtractRequest>({
      query: body => ({ url: '/extract', method: 'POST', body }),
      invalidatesTags: (result?: ApiEnvelope) => (result && result.errno === 0 ? ['Dashboard', 'Records', 'Extracts'] : []),
    }),
    extractHistory: builder.query<PagedList<IExtractRow>, { page: number; limit: number }>({
      query: params => ({ url: '/extract/history', params }),
      transformResponse: (r: ApiEnvelope<PagedList<IExtractRow>>) => isoTimes(r?.data ?? emptyPage<IExtractRow>()),
      providesTags: ['Extracts'],
    }),
  }),
});

export const {
  useGetDashboardQuery,
  useListRecordsQuery,
  useListTeamQuery,
  useGetLinksQuery,
  useRequestExtractMutation,
  useExtractHistoryQuery,
} = affiliateApi;
