import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { PagedList } from 'app/shared/model/admin/promotion-system.model';
import { getAdminToken } from 'app/shared/reducers/admin-auth';

// RTK Query client for the aftersale/RMA admin queue, served by
// litemall-order's LitemallAdminAftersaleController under
// /srv/private/admin/aftersale (routed to order-service by the admin-order
// gateway route). The list is a legacy {errno, data:{list,total,...}}
// envelope; approve/reject return an OrderOperationDtoResponse
// ({success, message, ...}) with a non-2xx HTTP status on failure.

// AftersaleDtoResponse (see litemall-order/docs/aftersale-vertical.md).
// type: 0 refund-only, 1 return-and-refund.
export interface IAftersale {
  id?: number;
  aftersaleSn?: string;
  orderId?: number;
  userId?: number;
  type?: number;
  reason?: string;
  amount?: number;
  pictures?: string[];
  comment?: string;
  status?: number;
  statusText?: string;
  handleTime?: string;
  addTime?: string;
}

// Aftersale status codes (aftersale-vertical.md).
export const AFTERSALE_STATUS: Record<number, string> = {
  1: 'applied',
  2: 'approved',
  3: 'refunded',
  4: 'rejected',
  5: 'cancelled',
};

export interface AftersaleListParams {
  page: number;
  limit: number;
  status?: number;
  orderId?: number;
  userId?: number;
}

export interface OrderOperation {
  success: boolean;
  message?: string;
  status?: string;
  operationType?: string;
  errorCode?: string;
}

interface ApiEnvelope<T> {
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

export const adminAftersaleApi = createApi({
  reducerPath: 'adminAftersaleApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin/aftersale',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      return headers;
    },
  }),
  tagTypes: ['Aftersale'],
  endpoints: builder => ({
    listAftersales: builder.query<PagedList<IAftersale>, AftersaleListParams>({
      query: ({ page, limit, status, orderId, userId }) => ({ url: '/list', params: clean({ page, limit, status, orderId, userId }) }),
      transformResponse: (r: ApiEnvelope<PagedList<IAftersale>>) => r?.data ?? emptyPage<IAftersale>(),
      providesTags: ['Aftersale'],
    }),
    approveAftersale: builder.mutation<OrderOperation, number>({
      query: id => ({ url: `/${id}/approve`, method: 'POST' }),
      invalidatesTags: ['Aftersale'],
    }),
    rejectAftersale: builder.mutation<OrderOperation, { id: number; reason?: string }>({
      query: ({ id, reason }) => ({ url: `/${id}/reject`, method: 'POST', body: reason ? { reason } : {} }),
      invalidatesTags: ['Aftersale'],
    }),
  }),
});

// Normalise an approve/reject mutation result into an error message (null on
// success) — same shape logic as promotionOpMessage but for order's
// OrderOperationDtoResponse.
export const aftersaleOpMessage = (res: unknown): string | null => {
  const r = res as { data?: OrderOperation; error?: { status?: number | string; data?: OrderOperation } };
  if (r && 'error' in r && r.error) {
    return r.error.data?.message || `Request failed (${r.error.status ?? 'network'})`;
  }
  if (r && 'data' in r && r.data) {
    return r.data.success ? null : r.data.message || 'Request failed.';
  }
  return 'Request failed.';
};

export const { useListAftersalesQuery, useApproveAftersaleMutation, useRejectAftersaleMutation } = adminAftersaleApi;
