import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { getAdminToken } from 'app/shared/reducers/admin-auth';
import { fromServerDateTime } from 'app/shared/util/server-datetime';

// RTK Query client for the ADMIN side of the Wave-5 affiliate program:
//   - Promoter management → gateway edge   /srv/private/admin/promoter/{list,toggle,ledger}
//     (EdgeAdminPromoterController — edge-local JDBC until order's litemall-db
//     hand-edits land)
//   - Withdrawal console  → litemall-order /srv/private/admin/extract/{list,approve,reject}
//     (order's Wave-5 admin surface; shapes read defensively until its
//     docs/handoff-affiliate-portal.md is committed)
// Legacy envelope {errno,errmsg,data}; mutations return the raw envelope.

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

export interface IPromoterRow {
  id: number;
  username?: string;
  nickname?: string;
  mobile?: string;
  avatar?: string;
  isPromoter?: boolean;
  spreadUid?: number;
  spreadCount?: number;
  payCount?: number;
  brokeragePrice?: number;
  addTime?: string;
}

/** V7 litemall_user_brokerage_record row (snake_case straight from the edge JDBC read). */
export interface ILedgerRow {
  id?: number;
  link_id?: string;
  link_type?: string;
  pm?: number;
  title?: string;
  price?: number;
  balance?: number;
  mark?: string;
  status?: number;
  freeze_time?: string;
  unfreeze_time?: string;
  add_time?: string;
}

export interface PromoterLedger extends PagedList<ILedgerRow> {
  user?: IPromoterRow;
}

/** litemall_user_extract row. status: 0 pending, 1 processing, 2 completed, -1 rejected. */
export interface IAdminExtractRow {
  id?: number;
  userId?: number;
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

const clean = (params: Record<string, unknown>): Record<string, unknown> => {
  const out: Record<string, unknown> = {};
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') out[k] = v;
  });
  return out;
};

const isoTimes = <T extends { addTime?: unknown; failTime?: unknown }>(page: PagedList<T>): PagedList<T> => ({
  ...page,
  list: (page.list ?? []).map(row => ({
    ...row,
    addTime: fromServerDateTime(row.addTime),
    failTime: fromServerDateTime(row.failTime),
  })),
});

export const adminAffiliateApi = createApi({
  reducerPath: 'adminAffiliateApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      return headers;
    },
  }),
  tagTypes: ['Promoters', 'Extracts'],
  endpoints: builder => ({
    listPromoters: builder.query<PagedList<IPromoterRow>, { q?: string; promotersOnly?: boolean; page: number; limit: number }>({
      query: params => ({ url: '/promoter/list', params: clean(params) }),
      transformResponse: (r: ApiEnvelope<PagedList<IPromoterRow>>) => isoTimes(r?.data ?? emptyPage<IPromoterRow>()),
      providesTags: ['Promoters'],
    }),
    togglePromoter: builder.mutation<ApiEnvelope<IPromoterRow>, { userId: number; promoter: boolean }>({
      query: body => ({ url: '/promoter/toggle', method: 'POST', body }),
      invalidatesTags: (result?: ApiEnvelope) => (result && result.errno === 0 ? ['Promoters'] : []),
    }),
    promoterLedger: builder.query<PromoterLedger, { userId: number; page: number; limit: number }>({
      query: params => ({ url: '/promoter/ledger', params }),
      transformResponse: (r: ApiEnvelope<PromoterLedger>) => r?.data ?? { ...emptyPage<ILedgerRow>() },
    }),

    // ----- Withdrawal approval console (order-service) --------------------
    listExtracts: builder.query<PagedList<IAdminExtractRow>, { status?: number | string; page: number; limit: number }>({
      query: params => ({ url: '/extract/list', params: clean(params) }),
      transformResponse: (r: ApiEnvelope<PagedList<IAdminExtractRow>>) => isoTimes(r?.data ?? emptyPage<IAdminExtractRow>()),
      providesTags: ['Extracts'],
    }),
    // Approve only transitions status 0 rows (guarded server-side).
    approveExtract: builder.mutation<ApiEnvelope, { id: number }>({
      query: body => ({ url: '/extract/approve', method: 'POST', body }),
      invalidatesTags: (result?: ApiEnvelope) => (result && result.errno === 0 ? ['Extracts'] : []),
    }),
    // Reject refunds brokerage_price and records the reason as fail_msg.
    rejectExtract: builder.mutation<ApiEnvelope, { id: number; reason: string }>({
      query: body => ({ url: '/extract/reject', method: 'POST', body }),
      invalidatesTags: (result?: ApiEnvelope) => (result && result.errno === 0 ? ['Extracts'] : []),
    }),
  }),
});

export const {
  useListPromotersQuery,
  useTogglePromoterMutation,
  usePromoterLedgerQuery,
  useListExtractsQuery,
  useApproveExtractMutation,
  useRejectExtractMutation,
} = adminAffiliateApi;
