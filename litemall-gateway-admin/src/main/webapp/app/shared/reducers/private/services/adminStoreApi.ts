import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { getAdminToken } from 'app/shared/reducers/admin-auth';

// RTK Query client for the physical-store vertical served by litemall-order
// through the gateway:
//   - Store CRUD          /srv/private/admin/store/{list,read,create,update,delete}
//   - Pickup write-off    /srv/private/admin/order/writeoff  (GET = preview by
//                         verifyCode, POST = commit — completes the pickup)
//
// NOTE: built against the ASSUMED Wave-4 contract (no committed handoff spec
// under litemall-order/docs/ yet). Field names on the wire may drift, so every
// read path normalises through a tolerant mapper instead of trusting exact
// keys. The litemall envelope is HTTP 200 even on business errors
// ({errno,errmsg,data}); mutations return the raw envelope so views can
// surface the backend's exact errmsg (the write-off commit has three distinct
// business failures: unknown code / already verified / wrong order state).

export interface ApiEnvelope<T = unknown> {
  errno: number;
  errmsg: string;
  data?: T;
}

export interface IStore {
  id?: number;
  name?: string;
  intro?: string;
  phone?: string;
  address?: string;
  detailedAddress?: string;
  logo?: string;
  latitude?: number;
  longitude?: number;
  businessHours?: string;
  isShow?: boolean;
  addTime?: string;
}

export interface StorePage {
  list: IStore[];
  total: number;
  pages: number;
}

export interface StoreListParams {
  page: number;
  limit: number;
  name?: string;
}

export interface IWriteoffGoods {
  goodsName?: string;
  number?: number;
  price?: number;
  picUrl?: string;
  specifications?: string[];
}

export interface IWriteoffPreview {
  id?: number;
  orderSn?: string;
  pickupName?: string;
  pickupMobile?: string;
  storeName?: string;
  actualPrice?: number;
  orderStatus?: number | string;
  orderStatusText?: string;
  goodsList: IWriteoffGoods[];
}

type Raw = Record<string, unknown>;

const str = (v: unknown): string | undefined => (typeof v === 'string' && v !== '' ? v : typeof v === 'number' ? String(v) : undefined);

const num = (v: unknown): number | undefined => {
  if (typeof v === 'number' && !Number.isNaN(v)) return v;
  if (typeof v === 'string' && v.trim() !== '' && !Number.isNaN(Number(v))) return Number(v);
  return undefined;
};

const bool = (v: unknown): boolean | undefined => {
  if (typeof v === 'boolean') return v;
  if (typeof v === 'number') return v !== 0;
  if (v === 'true' || v === '1') return true;
  if (v === 'false' || v === '0') return false;
  return undefined;
};

// First defined value among candidate keys — tolerates field-name drift.
const pick = (raw: Raw, ...keys: string[]): unknown => {
  for (const k of keys) {
    if (raw[k] !== undefined && raw[k] !== null) return raw[k];
  }
  return undefined;
};

const normalizeStore = (raw: unknown): IStore => {
  const r = (raw ?? {}) as Raw;
  return {
    id: num(pick(r, 'id', 'storeId')),
    name: str(pick(r, 'name', 'storeName')),
    intro: str(pick(r, 'intro', 'introduction', 'desc', 'description')),
    phone: str(pick(r, 'phone', 'mobile', 'tel', 'telephone')),
    address: str(pick(r, 'address', 'storeAddress')),
    detailedAddress: str(pick(r, 'detailedAddress', 'detailAddress', 'addressDetail')),
    logo: str(pick(r, 'logo', 'logoUrl', 'image', 'picUrl')),
    latitude: num(pick(r, 'latitude', 'lat')),
    longitude: num(pick(r, 'longitude', 'lng', 'lon')),
    businessHours: str(pick(r, 'businessHours', 'dayTime', 'openingHours', 'hours')),
    isShow: bool(pick(r, 'isShow', 'show', 'status', 'enabled')),
    addTime: str(pick(r, 'addTime', 'createTime', 'createdAt')),
  };
};

// Tolerates {list,total,pages}, {items,total}, or a bare array.
const normalizePage = (data: unknown, limit: number): StorePage => {
  let rows: unknown[] = [];
  let total = 0;
  let pages = 0;
  if (Array.isArray(data)) {
    rows = data;
    total = data.length;
  } else if (data && typeof data === 'object') {
    const d = data as Raw;
    rows = (Array.isArray(d.list) ? d.list : Array.isArray(d.items) ? d.items : Array.isArray(d.records) ? d.records : []) as unknown[];
    total = num(pick(d, 'total', 'count')) ?? rows.length;
    pages = num(pick(d, 'pages', 'totalPages')) ?? (limit > 0 ? Math.ceil(total / limit) : 0);
  }
  return { list: rows.map(normalizeStore), total, pages };
};

const normalizeGoods = (raw: unknown): IWriteoffGoods => {
  const r = (raw ?? {}) as Raw;
  const specs = pick(r, 'specifications', 'specs');
  return {
    goodsName: str(pick(r, 'goodsName', 'name', 'title')),
    number: num(pick(r, 'number', 'quantity', 'count', 'num')),
    price: num(pick(r, 'price', 'goodsPrice', 'actualPrice')),
    picUrl: str(pick(r, 'picUrl', 'pic', 'image', 'goodsPic')),
    specifications: Array.isArray(specs) ? specs.map(s => String(s)) : undefined,
  };
};

const normalizePreview = (data: unknown): IWriteoffPreview => {
  const r = (data ?? {}) as Raw;
  // The preview may be flat or nested under an 'order' key.
  const order = (r.order && typeof r.order === 'object' ? (r.order as Raw) : r) as Raw;
  const goods = pick(r, 'goodsList', 'items', 'orderGoods', 'goods') ?? pick(order, 'goodsList', 'items', 'orderGoods', 'goods');
  return {
    id: num(pick(order, 'id', 'orderId')),
    orderSn: str(pick(order, 'orderSn', 'sn', 'orderNo')),
    pickupName: str(pick(order, 'pickupName', 'consignee', 'realName', 'name')),
    pickupMobile: str(pick(order, 'pickupMobile', 'mobile', 'phone', 'userPhone')),
    storeName: str(pick(order, 'storeName', 'shopName')) ?? str((r.store as Raw | undefined)?.name),
    actualPrice: num(pick(order, 'actualPrice', 'payPrice', 'orderPrice')),
    orderStatus: pick(order, 'orderStatus', 'status') as number | string | undefined,
    orderStatusText: str(pick(order, 'orderStatusText', 'statusText', 'orderStatusDesc')),
    goodsList: Array.isArray(goods) ? goods.map(normalizeGoods) : [],
  };
};

const clean = (params: Record<string, unknown>): Record<string, unknown> => {
  const out: Record<string, unknown> = {};
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') out[k] = v;
  });
  return out;
};

export const adminStoreApi = createApi({
  reducerPath: 'adminStoreApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      return headers;
    },
  }),
  tagTypes: ['Store'],
  endpoints: builder => ({
    // ----- Store CRUD ----------------------------------------------------
    listStores: builder.query<StorePage, StoreListParams>({
      query: ({ page, limit, name }) => ({ url: '/store/list', params: clean({ page, limit, name }) }),
      transformResponse: (r: ApiEnvelope, _meta, arg) => normalizePage(r?.data, arg.limit),
      providesTags: ['Store'],
    }),
    readStore: builder.query<IStore, number | string>({
      query: id => ({ url: '/store/read', params: { id } }),
      transformResponse: (r: ApiEnvelope) => normalizeStore(r?.data),
      providesTags: ['Store'],
    }),
    createStore: builder.mutation<ApiEnvelope<IStore>, IStore>({
      query: body => ({ url: '/store/create', method: 'POST', body }),
      invalidatesTags: ['Store'],
    }),
    updateStore: builder.mutation<ApiEnvelope<IStore>, IStore>({
      query: body => ({ url: '/store/update', method: 'POST', body }),
      invalidatesTags: ['Store'],
    }),
    deleteStore: builder.mutation<ApiEnvelope, { id: number | string }>({
      query: body => ({ url: '/store/delete', method: 'POST', body }),
      invalidatesTags: ['Store'],
    }),

    // ----- Pickup write-off (核销) ----------------------------------------
    // Preview keeps the whole envelope: errno !== 0 (unknown code / already
    // verified / wrong state) must surface the backend's exact errmsg.
    previewWriteoff: builder.query<ApiEnvelope<IWriteoffPreview>, string>({
      query: verifyCode => ({ url: '/order/writeoff', params: { verifyCode } }),
      transformResponse: (r: ApiEnvelope) => ({
        errno: r?.errno ?? -1,
        errmsg: r?.errmsg ?? 'Malformed response',
        data: r?.errno === 0 ? normalizePreview(r?.data) : undefined,
      }),
      // Each scan must hit the backend fresh — never serve a stale preview.
      keepUnusedDataFor: 0,
    }),
    commitWriteoff: builder.mutation<ApiEnvelope, { verifyCode: string }>({
      query: body => ({ url: '/order/writeoff', method: 'POST', body }),
    }),
  }),
});

export const {
  useListStoresQuery,
  useReadStoreQuery,
  useCreateStoreMutation,
  useUpdateStoreMutation,
  useDeleteStoreMutation,
  useLazyPreviewWriteoffQuery,
  useCommitWriteoffMutation,
} = adminStoreApi;
