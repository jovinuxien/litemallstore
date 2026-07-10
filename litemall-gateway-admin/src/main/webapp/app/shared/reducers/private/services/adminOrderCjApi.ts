import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { getAdminToken } from 'app/shared/reducers/admin-auth';

// RTK Query client for litemall-order's CJ admin surface (Wave 3, order
// worktree Task E):
//   GET /srv/private/admin/order/{orderId}/tracking  — carrier + tracking
//       number + event list; a clean "not shipped" payload when no tracking
//       exists yet.
//   GET /srv/private/admin/order/cj/balance          — CJ account balance.
//
// DEPENDENCY: these endpoints are NOT live yet — order's Task E had no
// committed handoff spec when this client was written, so the shapes below
// are the ASSUMED contract (docs/handoff-order-admin-cj.md). Everything is
// parsed defensively (envelope-or-bare, field aliases) and every failure mode
// (404, 5xx, errno != 0, network) degrades to `available: false` so the panel
// renders a muted note instead of an error. Reconcile transformResponse
// against litemall-order/docs/ when the real spec lands.

export interface ITrackingEvent {
  time?: string;
  status?: string;
  description?: string;
  location?: string;
}

export interface ITracking {
  /** false when the endpoint itself is missing/erroring (vs a clean "not shipped"). */
  available: boolean;
  shipped: boolean;
  carrier?: string;
  trackNumber?: string;
  cjOrderStatus?: string;
  events: ITrackingEvent[];
}

export interface ICjBalance {
  available: boolean;
  amount?: number;
  currency?: string;
}

interface MaybeEnvelope {
  errno?: number;
  errmsg?: string;
  data?: Record<string, unknown>;
  [k: string]: unknown;
}

// Accept both the legacy {errno,data} envelope and a bare DTO.
const payloadOf = (r: MaybeEnvelope | undefined): Record<string, unknown> | null => {
  if (!r || typeof r !== 'object') return null;
  if (typeof r.errno === 'number') return r.errno === 0 ? ((r.data ?? {}) as Record<string, unknown>) : null;
  return r as Record<string, unknown>;
};

const str = (v: unknown): string | undefined => (typeof v === 'string' && v ? v : undefined);

const toTracking = (r: MaybeEnvelope): ITracking => {
  const p = payloadOf(r);
  if (!p) return { available: false, shipped: false, events: [] };
  const rawEvents = (p.events ?? p.trackInfo ?? p.trackingEvents ?? []) as Array<Record<string, unknown>>;
  const events: ITrackingEvent[] = Array.isArray(rawEvents)
    ? rawEvents.map(e => ({
        time: str(e.time) ?? str(e.date) ?? str(e.eventTime),
        status: str(e.status) ?? str(e.trackingStatus),
        description: str(e.description) ?? str(e.content) ?? str(e.detail),
        location: str(e.location) ?? str(e.area),
      }))
    : [];
  const trackNumber = str(p.trackNumber) ?? str(p.trackingNumber);
  const shippedFlag = p.shipped;
  return {
    available: true,
    shipped: typeof shippedFlag === 'boolean' ? shippedFlag : Boolean(trackNumber || events.length),
    carrier: str(p.carrier) ?? str(p.logisticName),
    trackNumber,
    cjOrderStatus: str(p.cjOrderStatus) ?? str(p.orderStatus),
    events,
  };
};

const toBalance = (r: MaybeEnvelope): ICjBalance => {
  const p = payloadOf(r);
  if (!p) return { available: false };
  const amount = p.amount ?? p.balance;
  if (typeof amount !== 'number' && typeof amount !== 'string') return { available: false };
  return { available: true, amount: Number(amount), currency: str(p.currency) ?? 'USD' };
};

export const adminOrderCjApi = createApi({
  reducerPath: 'adminOrderCjApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin/order',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      return headers;
    },
  }),
  endpoints: builder => ({
    getTracking: builder.query<ITracking, number | string>({
      query: orderId => ({ url: `/${orderId}/tracking` }),
      transformResponse: toTracking,
      // Any transport/HTTP error becomes a soft "unavailable" result so the
      // panel never breaks the order page while order Task E is unshipped.
      transformErrorResponse: (): ITracking => ({ available: false, shipped: false, events: [] }),
    }),
    getCjBalance: builder.query<ICjBalance, void>({
      query: () => ({ url: '/cj/balance' }),
      transformResponse: toBalance,
      transformErrorResponse: (): ICjBalance => ({ available: false }),
    }),
  }),
});

export const { useGetTrackingQuery, useGetCjBalanceQuery } = adminOrderCjApi;
