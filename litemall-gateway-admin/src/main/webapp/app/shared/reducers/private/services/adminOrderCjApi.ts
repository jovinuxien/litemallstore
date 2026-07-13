import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { getAdminToken } from 'app/shared/reducers/admin-auth';

// RTK Query client for litemall-order's admin operations surface (Wave 3
// tracking/balance + Wave 4 ops), all under /srv/private/admin:
//   GET  /order/{orderId}/tracking       — carrier + tracking number + events;
//        Wave 4 adds a nullable `note` and local-order tracking.
//   GET  /order/cj/balance               — CJ account balance.
//   POST /order/{orderId}/pay            — offline mark-paid (tender OFFLINE:...);
//        for source='cj' orders the backend live-fires the CJ createOrderV2
//        replay at mark-paid.
//   GET  /order/stat/channel             — by-source / by-tender counts.
//   GET  /order/fulfillment/config       — receipt-printing flags.
//   POST /order/{orderId}/print-receipt  — errno 641 disabled / 642 failed.
//   POST /aftersale/batch-approve|batch-reject — partial-success envelope.
//
// The tracking/balance shapes follow the committed Wave-3 handoff
// (litemall-order/docs/handoff-gateway-admin-cj-tracking.md); the Wave-4
// endpoints (pay, stat/channel, fulfillment, print-receipt, aftersale batch)
// had no committed spec when this was written and follow the ASSUMED Wave-4
// contract. Everything is parsed defensively (envelope-or-bare, field
// aliases) and read paths degrade to `available: false` so panels render a
// muted note instead of an error. Reconcile against litemall-order/docs/ when
// the amended specs land.

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
  /** Wave-4 nullable info line, e.g. "tracking provider disabled". */
  note?: string;
  events: ITrackingEvent[];
}

export interface ICjBalance {
  available: boolean;
  amount?: number;
  currency?: string;
}

export interface IChannelRow {
  key: string;
  count: number;
  amount?: number;
}

export interface IChannelStat {
  available: boolean;
  bySource: IChannelRow[];
  byTender: IChannelRow[];
}

export interface IFulfillmentConfig {
  available: boolean;
  printerEnabled?: boolean;
  provider?: string;
  autoPrint?: boolean;
  /** every flag the backend sent, for tolerant rendering */
  flags: Record<string, unknown>;
}

export interface IBatchResult {
  ok: boolean;
  errmsg?: string;
  succeeded: Array<number | string>;
  failed: Array<{ id: number | string; errmsg?: string }>;
}

export interface IEnvelope {
  errno?: number;
  errmsg?: string;
  data?: unknown;
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

const num = (v: unknown): number | undefined => {
  const n = typeof v === 'number' ? v : typeof v === 'string' && v !== '' ? Number(v) : NaN;
  return Number.isFinite(n) ? n : undefined;
};

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
    note: str(p.note),
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

const channelRows = (raw: unknown, keyField: string, fallbackKey: string): IChannelRow[] =>
  Array.isArray(raw)
    ? (raw as Array<Record<string, unknown>>).map(e => ({
        key: str(e[keyField]) ?? fallbackKey,
        // Backend aliases count(*) AS orders (OrderMapper.xml); keep `count`
        // as a tolerant fallback.
        count: num(e.orders) ?? num(e.count) ?? 0,
        amount: num(e.amount),
      }))
    : [];

const toChannelStat = (r: MaybeEnvelope): IChannelStat => {
  const p = payloadOf(r);
  if (!p) return { available: false, bySource: [], byTender: [] };
  return {
    available: true,
    bySource: channelRows(p.bySource, 'source', 'unknown'),
    // null tender = order never paid; the backend maps it to UNPAID but
    // render tolerantly in case a null slips through.
    byTender: channelRows(p.byTender, 'tender', 'UNPAID'),
  };
};

const toFulfillmentConfig = (r: MaybeEnvelope): IFulfillmentConfig => {
  const p = payloadOf(r);
  if (!p) return { available: false, flags: {} };
  // Backend nests the readout: {printer:{provider,enabled,autoPrint,businessName},
  // express:{provider,enabled,cacheMinutes}} (LitemallAdminOrderController
  // /fulfillment/config). Keep the old flat reads as a tolerant fallback.
  const printer = (p.printer && typeof p.printer === 'object' ? p.printer : {}) as Record<string, unknown>;
  return {
    available: true,
    printerEnabled:
      typeof printer.enabled === 'boolean' ? printer.enabled : typeof p.printerEnabled === 'boolean' ? p.printerEnabled : undefined,
    provider: str(printer.provider) ?? str(p.provider),
    autoPrint: typeof printer.autoPrint === 'boolean' ? printer.autoPrint : typeof p.autoPrint === 'boolean' ? p.autoPrint : undefined,
    flags: p,
  };
};

// Partial-success envelope: {errno,data:{succeeded:[ids], failed:[{id,errmsg}]}}.
const toBatchResult = (r: MaybeEnvelope): IBatchResult => {
  if (r && typeof r.errno === 'number' && r.errno !== 0) {
    return { ok: false, errmsg: r.errmsg || `Request failed (errno ${r.errno})`, succeeded: [], failed: [] };
  }
  const p = payloadOf(r) ?? {};
  const succeeded = Array.isArray(p.succeeded) ? (p.succeeded as Array<number | string>) : [];
  const failedRaw = Array.isArray(p.failed) ? (p.failed as Array<unknown>) : [];
  const failed = failedRaw.map(f =>
    f && typeof f === 'object'
      ? { id: (f as Record<string, unknown>).id as number | string, errmsg: str((f as Record<string, unknown>).errmsg) }
      : { id: f as number | string },
  );
  return { ok: true, succeeded, failed };
};

export const adminOrderCjApi = createApi({
  reducerPath: 'adminOrderCjApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      return headers;
    },
  }),
  endpoints: builder => ({
    getTracking: builder.query<ITracking, number | string>({
      query: orderId => ({ url: `/order/${orderId}/tracking` }),
      transformResponse: toTracking,
      // Any transport/HTTP error becomes a soft "unavailable" result so the
      // panel never breaks the order page.
      transformErrorResponse: (): ITracking => ({ available: false, shipped: false, events: [] }),
    }),
    getCjBalance: builder.query<ICjBalance, void>({
      query: () => ({ url: '/order/cj/balance' }),
      transformResponse: toBalance,
      transformErrorResponse: (): ICjBalance => ({ available: false }),
    }),
    // Offline mark-paid; only legal from unpaid (101). Raw envelope returned —
    // use orderOpMessage() to normalise success/errno/HTTP failures.
    markOrderPaid: builder.mutation<IEnvelope, { orderId: number | string; reference?: string }>({
      query: ({ orderId, reference }) => ({
        url: `/order/${orderId}/pay`,
        method: 'POST',
        body: reference ? { reference } : {},
      }),
    }),
    getChannelStat: builder.query<IChannelStat, { start?: string; end?: string }>({
      query: ({ start, end }) => ({ url: '/order/stat/channel', params: { ...(start ? { start } : {}), ...(end ? { end } : {}) } }),
      transformResponse: toChannelStat,
      transformErrorResponse: (): IChannelStat => ({ available: false, bySource: [], byTender: [] }),
    }),
    getFulfillmentConfig: builder.query<IFulfillmentConfig, void>({
      query: () => ({ url: '/order/fulfillment/config' }),
      transformResponse: toFulfillmentConfig,
      transformErrorResponse: (): IFulfillmentConfig => ({ available: false, flags: {} }),
    }),
    // Raw envelope so the caller can map errno 641 (printing disabled) and
    // 642 (print failed) to distinct messages.
    printReceipt: builder.mutation<IEnvelope, number | string>({
      query: orderId => ({ url: `/order/${orderId}/print-receipt`, method: 'POST' }),
    }),
    batchApproveAftersales: builder.mutation<IBatchResult, Array<number>>({
      query: ids => ({ url: '/aftersale/batch-approve', method: 'POST', body: { ids } }),
      transformResponse: toBatchResult,
    }),
    batchRejectAftersales: builder.mutation<IBatchResult, Array<number>>({
      query: ids => ({ url: '/aftersale/batch-reject', method: 'POST', body: { ids } }),
      transformResponse: toBatchResult,
    }),
  }),
});

// Normalise a mutation result carrying the litemall envelope into an error
// message (null on success) — covers errno!==0 bodies AND non-2xx HTTP whose
// error body may itself be an errno envelope (e.g. mark-paid 422).
export const orderOpMessage = (res: unknown): string | null => {
  const r = res as { data?: IEnvelope; error?: { status?: number | string; data?: IEnvelope } };
  if (r && 'error' in r && r.error) {
    return r.error.data?.errmsg || `Request failed (${r.error.status ?? 'network'})`;
  }
  const env = r?.data;
  if (env && typeof env.errno === 'number') {
    return env.errno === 0 ? null : env.errmsg || `Request failed (errno ${env.errno})`;
  }
  return env ? null : 'Request failed.';
};

// ----- CSV export (plain fetch, not RTK — we need a blob download) ---------

export interface OrderExportFilters {
  orderSn?: string;
  orderStatusArray?: number[];
  sort?: string;
  order?: string;
  deliveryType?: string;
  /** ISO date-times, e.g. 2026-07-01T00:00:00 */
  start?: string;
  end?: string;
}

// GET /srv/private/admin/order/export with the same filters as the admin list
// (+ start/end), streamed as text/csv (UTF-8 BOM, attachment; over-cap files
// end with a "# TRUNCATED" trailer). Triggers a browser download; resolves to
// null on success or an error message (non-2xx / errno-JSON body / network).
export const downloadOrderExport = async (filters: OrderExportFilters): Promise<string | null> => {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([k, v]) => {
    if (v === undefined || v === null || v === '') return;
    params.set(k, Array.isArray(v) ? v.join(',') : String(v));
  });
  const token = getAdminToken();
  try {
    const res = await fetch(`/srv/private/admin/order/export?${params.toString()}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    const contentType = res.headers.get('content-type') ?? '';
    if (!res.ok || contentType.includes('application/json')) {
      try {
        const body = (await res.json()) as IEnvelope;
        return body?.errmsg || `Export failed (${res.status})`;
      } catch {
        return `Export failed (${res.status})`;
      }
    }
    const blob = await res.blob();
    const disposition = res.headers.get('content-disposition') ?? '';
    const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
    const filename = match?.[1] ?? `orders-${new Date().toISOString().slice(0, 10)}.csv`;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    return null;
  } catch {
    return 'Export failed — network error.';
  }
};

export const {
  useGetTrackingQuery,
  useGetCjBalanceQuery,
  useMarkOrderPaidMutation,
  useGetChannelStatQuery,
  useGetFulfillmentConfigQuery,
  usePrintReceiptMutation,
  useBatchApproveAftersalesMutation,
  useBatchRejectAftersalesMutation,
} = adminOrderCjApi;
