import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { getAdminToken } from 'app/shared/reducers/admin-auth';

// RTK Query client for the freight-template admin vertical, served by
// litemall-order through the gateway under '/srv/private/admin/freight'.
//
// CONTRACT NOTE: built against the ASSUMED Wave-4 contract (no freight handoff
// spec was committed under litemall-order/docs/ at build time — checked
// 2026-07-13). Every DTO mapping lives in THIS file (normalize* helpers +
// toWriteBody) with tolerant fallbacks, so when the real backend lands only
// this file needs realigning. TODO(freight-contract): re-verify field names
// against the committed handoff spec once litemall-order publishes it.
//
// Envelope rule: litemall returns HTTP 200 with {errno,errmsg,data} even on
// business errors — mutations return the raw envelope so views can surface
// errmsg inline (e.g. delete refused while a live goods references the
// template). Exception: /selectlist is a BARE JSON ARRAY (no envelope) per the
// promotion-style convention — the transform handles both shapes defensively.

export interface ApiEnvelope<T = unknown> {
  errno: number;
  errmsg: string;
  data?: T;
}

// ----- Domain shapes (crmeb-parity design) ----------------------------------

export interface IFreightTemplate {
  id?: number;
  name: string;
  /** 0 = charge by region rows only, 1 = free-shipping rules apply on top,
   *  2 = always free. TODO(freight-contract): confirm enum meaning. */
  appoint: number;
  sortOrder?: number;
  isDefault?: boolean;
}

export interface IFreightRegionRow {
  id?: number;
  /** ISO country code; '*' = any country (fallback row). */
  countryCode: string;
  provinceName?: string;
  /** crmeb first/continue formula: first `first` units cost `firstPrice`,
   *  each further `continue` units add `continuePrice`. */
  first: number;
  firstPrice: number;
  continue: number;
  continuePrice: number;
}

export interface IFreightFreeRule {
  id?: number;
  countryCode: string;
  provinceName?: string;
  /** Free when quantity >= number OR order amount >= price (OR semantics). */
  number: number;
  price: number;
}

export interface IFreightTemplateDetail {
  template: IFreightTemplate;
  regions: IFreightRegionRow[];
  freeRules: IFreightFreeRule[];
}

export interface FreightTemplatePage {
  list: IFreightTemplate[];
  total: number;
  pages?: number;
}

export interface FreightListParams {
  page: number;
  limit: number;
}

export interface FreightSelectOption {
  id: number;
  name: string;
}

export interface FreightPreviewParams {
  tempId: number | string;
  countryCode: string;
  province?: string;
  quantity: number | string;
  weight?: number | string;
}

/** Normalised dry-run quote. `extras` carries any scalar fields the backend
 *  returns that we do not know by name, so the widget can render whatever
 *  comes back without crashing. */
export interface IFreightPreview {
  errno: number;
  errmsg?: string;
  source?: string;
  amount?: number;
  free?: boolean;
  breakdown: Record<string, unknown>[];
  extras: [string, string][];
}

// ----- Tolerant DTO mapping (single source of truth) -------------------------

const asNumber = (v: unknown, fallback = 0): number => {
  const n = Number(v);
  return Number.isFinite(n) ? n : fallback;
};

const asRecord = (v: unknown): Record<string, unknown> => (v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {});

const asArray = (v: unknown): Record<string, unknown>[] => (Array.isArray(v) ? v.map(asRecord) : []);

const pick = (row: Record<string, unknown>, ...keys: string[]): unknown => {
  for (const k of keys) {
    if (row[k] !== undefined && row[k] !== null) return row[k];
  }
  return undefined;
};

// TODO(freight-contract): fallback key names below are guesses at plausible
// backend spellings; trim once the committed spec fixes them.
const normalizeTemplate = (raw: unknown): IFreightTemplate => {
  const row = asRecord(raw);
  return {
    id: row.id != null ? asNumber(row.id) : undefined,
    name: String(pick(row, 'name', 'templateName') ?? ''),
    appoint: asNumber(pick(row, 'appoint', 'type'), 0),
    sortOrder: pick(row, 'sortOrder', 'sort') != null ? asNumber(pick(row, 'sortOrder', 'sort')) : undefined,
    isDefault: Boolean(pick(row, 'isDefault', 'default', 'defaultFlag') ?? false),
  };
};

const normalizeRegion = (raw: unknown): IFreightRegionRow => {
  const row = asRecord(raw);
  return {
    id: row.id != null ? asNumber(row.id) : undefined,
    countryCode: String(pick(row, 'countryCode', 'country') ?? '*') || '*',
    provinceName: pick(row, 'provinceName', 'province') != null ? String(pick(row, 'provinceName', 'province')) : undefined,
    first: asNumber(pick(row, 'first', 'firstNumber', 'firstNum'), 1),
    firstPrice: asNumber(pick(row, 'firstPrice', 'firstFee')),
    continue: asNumber(pick(row, 'continue', 'continueNumber', 'renewal'), 1),
    continuePrice: asNumber(pick(row, 'continuePrice', 'renewalPrice', 'continueFee')),
  };
};

const normalizeFreeRule = (raw: unknown): IFreightFreeRule => {
  const row = asRecord(raw);
  return {
    id: row.id != null ? asNumber(row.id) : undefined,
    countryCode: String(pick(row, 'countryCode', 'country') ?? '*') || '*',
    provinceName: pick(row, 'provinceName', 'province') != null ? String(pick(row, 'provinceName', 'province')) : undefined,
    number: asNumber(pick(row, 'number', 'num')),
    price: asNumber(pick(row, 'price', 'amount')),
  };
};

const normalizeDetail = (data: unknown): IFreightTemplateDetail => {
  const d = asRecord(data);
  // Wrapper keys are contract guesses: {template, regions, freeRules} first,
  // then plausible variants, then a flat row (template fields at top level).
  const templateRaw = pick(d, 'template', 'freightTemplate') ?? d;
  return {
    template: normalizeTemplate(templateRaw),
    regions: asArray(pick(d, 'regions', 'regionList', 'regionRows', 'rows')).map(normalizeRegion),
    freeRules: asArray(pick(d, 'freeRules', 'freeList', 'frees', 'freeRows')).map(normalizeFreeRule),
  };
};

const normalizePage = (data: unknown): FreightTemplatePage => {
  if (Array.isArray(data)) return { list: data.map(normalizeTemplate), total: data.length };
  const d = asRecord(data);
  const list = asArray(pick(d, 'list', 'items', 'records')).map(normalizeTemplate);
  return {
    list,
    total: asNumber(pick(d, 'total', 'count'), list.length),
    pages: pick(d, 'pages') != null ? asNumber(d.pages) : undefined,
  };
};

const normalizePreview = (raw: unknown): IFreightPreview => {
  // May arrive as an envelope or as a bare payload — unwrap defensively.
  const outer = asRecord(raw);
  const isEnvelope = typeof outer.errno === 'number';
  if (isEnvelope && outer.errno !== 0) {
    return { errno: outer.errno as number, errmsg: String(outer.errmsg ?? `Preview failed (errno ${outer.errno})`), breakdown: [], extras: [] };
  }
  const data = isEnvelope ? asRecord(outer.data) : outer;
  const amountRaw = pick(data, 'freightPrice', 'amount', 'price', 'freight', 'total');
  const known = new Set(['freightPrice', 'amount', 'price', 'freight', 'total', 'source', 'free', 'breakdown', 'lines', 'items']);
  const extras: [string, string][] = Object.entries(data)
    .filter(([k, v]) => !known.has(k) && v != null && typeof v !== 'object')
    .map(([k, v]) => [k, String(v)] as [string, string]);
  return {
    errno: 0,
    source: data.source != null ? String(data.source) : undefined,
    amount: amountRaw != null ? asNumber(amountRaw) : undefined,
    free: data.free != null ? Boolean(data.free) : undefined,
    breakdown: asArray(pick(data, 'breakdown', 'lines', 'items')),
    extras,
  };
};

// Write payload: flat template fields (litemall body style) plus the two row
// arrays. TODO(freight-contract): confirm whether the backend wants flat or a
// nested {template:{...}} wrapper; flat matches every other litemall admin
// create/update body in this SPA.
const toWriteBody = (detail: IFreightTemplateDetail): Record<string, unknown> => ({
  ...detail.template,
  regions: detail.regions,
  freeRules: detail.freeRules,
});

const clean = (params: Record<string, unknown>): Record<string, unknown> => {
  const out: Record<string, unknown> = {};
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') out[k] = v;
  });
  return out;
};

// ----- API slice --------------------------------------------------------------

export const adminFreightApi = createApi({
  reducerPath: 'adminFreightApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin/freight',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      return headers;
    },
  }),
  tagTypes: ['FreightTemplate'],
  endpoints: builder => ({
    listFreightTemplates: builder.query<FreightTemplatePage, FreightListParams>({
      query: ({ page, limit }) => ({ url: '/list', params: { page, limit } }),
      transformResponse: (r: ApiEnvelope<unknown> | unknown) =>
        normalizePage(Array.isArray(r) ? r : (r as ApiEnvelope<unknown>)?.data),
      providesTags: ['FreightTemplate'],
    }),
    freightTemplateDetail: builder.query<IFreightTemplateDetail, number | string>({
      query: id => ({ url: '/detail', params: { id } }),
      transformResponse: (r: ApiEnvelope<unknown>) => normalizeDetail(r?.data),
      providesTags: (_res, _err, id) => [{ type: 'FreightTemplate', id }],
    }),
    createFreightTemplate: builder.mutation<ApiEnvelope, IFreightTemplateDetail>({
      query: detail => ({ url: '/create', method: 'POST', body: toWriteBody(detail) }),
      invalidatesTags: ['FreightTemplate'],
    }),
    updateFreightTemplate: builder.mutation<ApiEnvelope, IFreightTemplateDetail>({
      query: detail => ({ url: '/update', method: 'POST', body: toWriteBody(detail) }),
      invalidatesTags: ['FreightTemplate'],
    }),
    // Returns the raw envelope: delete answers errno != 0 (or HTTP 422) while a
    // live goods still references the template — the list surfaces errmsg inline.
    deleteFreightTemplate: builder.mutation<ApiEnvelope, { id: number }>({
      query: body => ({ url: '/delete', method: 'POST', body }),
      invalidatesTags: ['FreightTemplate'],
    }),
    setDefaultFreightTemplate: builder.mutation<ApiEnvelope, { id: number }>({
      query: body => ({ url: '/set-default', method: 'POST', body }),
      invalidatesTags: ['FreightTemplate'],
    }),
    // BARE JSON ARRAY (no envelope) per the contract — but handle an
    // {errno,data} envelope too, in case the backend lands enveloped.
    freightSelectList: builder.query<FreightSelectOption[], void>({
      query: () => ({ url: '/selectlist' }),
      transformResponse: (r: unknown): FreightSelectOption[] => {
        const rows = Array.isArray(r)
          ? r
          : (() => {
              const data = (r as ApiEnvelope<unknown>)?.data;
              if (Array.isArray(data)) return data;
              const list = asRecord(data).list;
              return Array.isArray(list) ? list : [];
            })();
        return rows
          .map(asRecord)
          .filter(row => row.id != null)
          .map(row => ({ id: asNumber(row.id), name: String(pick(row, 'name', 'templateName') ?? `#${row.id}`) }));
      },
      providesTags: ['FreightTemplate'],
    }),
    previewFreight: builder.query<IFreightPreview, FreightPreviewParams>({
      query: ({ tempId, countryCode, province, quantity, weight }) => ({
        url: '/preview',
        params: clean({ tempId, countryCode, province, quantity, weight }),
      }),
      transformResponse: normalizePreview,
    }),
  }),
});

export const {
  useListFreightTemplatesQuery,
  useFreightTemplateDetailQuery,
  useCreateFreightTemplateMutation,
  useUpdateFreightTemplateMutation,
  useDeleteFreightTemplateMutation,
  useSetDefaultFreightTemplateMutation,
  useFreightSelectListQuery,
  useLazyPreviewFreightQuery,
} = adminFreightApi;
