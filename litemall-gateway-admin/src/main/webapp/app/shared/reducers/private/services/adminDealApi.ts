import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { getAdminToken } from 'app/shared/reducers/admin-auth';
import { fromServerDateTime } from 'app/shared/util/server-datetime';

// RTK Query client for the Flash Deals admin vertical, served through the
// gateway under '/srv/private/admin/deal'.
//
// Envelope rule: litemall returns HTTP 200 with {errno,errmsg,data} even on
// business errors — mutations return the raw envelope so views can surface
// errmsg inline. Deal-specific errnos: 650 = invalid request (message in
// errmsg; since Wave 12 includes the CJ cost floor), 651 = conflict
// (overlapping window) or live-immutable field, 652 = CJ goods with no
// captured cost (CJ deals otherwise live since Wave 12).
//
// startTime/stopTime: the committed contract
// (handoff-gateway-admin-flash-deals.md) says ISO local datetime strings,
// but the Wave-12 goods-management build serializes LocalDateTime as
// numeric arrays module-wide — normalise both here (fromServerDateTime)
// so the panel survives either serialization.

export interface ApiEnvelope<T = unknown> {
  errno: number;
  errmsg: string;
  data?: T;
}

export interface IDeal {
  id?: number;
  goodsId?: number;
  goodsName?: string;
  picUrl?: string;
  dealPrice?: number;
  /** May be null when the goods carries no original retail price. */
  originalRetailPrice?: number | null;
  /** 0 = uncapped. */
  stock?: number;
  sales?: number;
  /** ISO LocalDateTime string, no timezone suffix. */
  startTime?: string;
  stopTime?: string;
  enabled?: boolean;
  /** Currently live (enabled + inside the window) — rendered as a red badge. */
  live?: boolean;
}

export interface DealPage {
  total: number;
  page: number;
  limit: number;
  list: IDeal[];
}

export interface DealListParams {
  page: number;
  limit: number;
}

export interface DealCreateBody {
  goodsId: number;
  dealPrice: number;
  startTime: string;
  stopTime: string;
  stock: number;
}

export interface DealUpdateBody {
  id: number;
  dealPrice?: number;
  startTime?: string;
  stopTime?: string;
  stock?: number;
  enabled?: boolean;
}

// Normalise an RTK-Query mutation result into an inline message (null on
// success): digs errmsg out of an HTTP-level error body too, so the 650/651/
// 652 validation errnos surface verbatim whichever way the backend answers.
export const dealOpMessage = (res: unknown): string | null => {
  const r = res as { data?: ApiEnvelope; error?: { status?: number | string; data?: ApiEnvelope } };
  if (r && 'error' in r && r.error) {
    return r.error.data?.errmsg || `Request failed (${r.error.status ?? 'network'})`;
  }
  if (r && 'data' in r && r.data && typeof r.data.errno === 'number') {
    return r.data.errno === 0 ? null : r.data.errmsg || `Request failed (errno ${r.data.errno})`;
  }
  return 'Request failed.';
};

const toDealView = (d: IDeal): IDeal => ({
  ...d,
  startTime: fromServerDateTime(d.startTime),
  stopTime: fromServerDateTime(d.stopTime),
});

export const adminDealApi = createApi({
  reducerPath: 'adminDealApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin/deal',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      return headers;
    },
  }),
  tagTypes: ['Deal'],
  endpoints: builder => ({
    listDeals: builder.query<DealPage, DealListParams>({
      query: ({ page, limit }) => ({ url: '/list', params: { page, limit } }),
      transformResponse: (r: ApiEnvelope<DealPage>, _meta, arg) => {
        const d = r?.data ?? { total: 0, page: arg.page, limit: arg.limit, list: [] };
        return { ...d, list: (d.list ?? []).map(toDealView) };
      },
      providesTags: ['Deal'],
    }),
    readDeal: builder.query<IDeal, number | string>({
      query: id => ({ url: '/read', params: { id } }),
      transformResponse: (r: ApiEnvelope<IDeal>) => toDealView(r?.data ?? {}),
      providesTags: (_res, _err, id) => [{ type: 'Deal', id }],
    }),
    createDeal: builder.mutation<ApiEnvelope, DealCreateBody>({
      query: body => ({ url: '/create', method: 'POST', body }),
      invalidatesTags: ['Deal'],
    }),
    updateDeal: builder.mutation<ApiEnvelope, DealUpdateBody>({
      query: body => ({ url: '/update', method: 'POST', body }),
      invalidatesTags: (_res, _err, body) => ['Deal', { type: 'Deal', id: body.id }],
    }),
    deleteDeal: builder.mutation<ApiEnvelope, { id: number }>({
      query: body => ({ url: '/delete', method: 'POST', body }),
      invalidatesTags: ['Deal'],
    }),
  }),
});

export const { useListDealsQuery, useReadDealQuery, useCreateDealMutation, useUpdateDealMutation, useDeleteDealMutation } = adminDealApi;
