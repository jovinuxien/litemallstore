import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { PagedList } from 'app/shared/model/admin/promotion-system.model';
import { getAdminToken } from 'app/shared/reducers/admin-auth';
import { fromServerDateTime } from 'app/shared/util/server-datetime';

// RTK Query client for the engagement admin lists (collect / footprint /
// feedback), served by litemall-goods-management's AdminEngagementController
// under /srv/private/admin/{collect,footprint,feedback}/list via the gateway's
// /srv/** catch-all. Read-only cross-user views with optional filters; legacy
// ResponseUtil.okList envelope {errno, data:{list,total,page,limit,pages}}.

export interface EngagementListParams {
  page: number;
  limit: number;
  sort?: string;
  order?: 'asc' | 'desc';
  userId?: string;
  /** collect only: the collected goods/topic id. */
  valueId?: string;
  /** footprint only. */
  goodsId?: string;
  /** feedback only. */
  username?: string;
}

// litemall_collect row: type 0 = goods, 1 = topic.
export interface ICollect {
  id?: number;
  userId?: number;
  valueId?: number;
  type?: number;
  addTime?: string;
}

export interface IFootprint {
  id?: number;
  userId?: number;
  goodsId?: number;
  addTime?: string;
}

export interface IFeedback {
  id?: number;
  userId?: number;
  username?: string;
  mobile?: string;
  feedType?: string;
  content?: string;
  status?: number;
  hasPicture?: boolean;
  picUrls?: string[];
  addTime?: string;
}

interface ApiEnvelope<T> {
  errno: number;
  errmsg: string;
  data?: T;
}

const emptyPage = <T>(): PagedList<T> => ({ list: [], total: 0, page: 1, limit: 0, pages: 0 });

// These rows serialize addTime as a LocalDateTime array — normalise to ISO.
const withIsoAddTime = <T extends { addTime?: unknown }>(page: PagedList<T>): PagedList<T> => ({
  ...page,
  list: (page.list ?? []).map(row => ({ ...row, addTime: fromServerDateTime(row.addTime) })),
});

const clean = (params: Record<string, unknown>): Record<string, unknown> => {
  const out: Record<string, unknown> = {};
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') out[k] = v;
  });
  return out;
};

export const adminEngagementApi = createApi({
  reducerPath: 'adminEngagementApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      return headers;
    },
  }),
  endpoints: builder => ({
    listCollects: builder.query<PagedList<ICollect>, EngagementListParams>({
      query: ({ page, limit, sort, order, userId, valueId }) => ({ url: '/collect/list', params: clean({ page, limit, sort, order, userId, valueId }) }),
      transformResponse: (r: ApiEnvelope<PagedList<ICollect>>) => withIsoAddTime(r?.data ?? emptyPage<ICollect>()),
    }),
    listFootprints: builder.query<PagedList<IFootprint>, EngagementListParams>({
      query: ({ page, limit, sort, order, userId, goodsId }) => ({ url: '/footprint/list', params: clean({ page, limit, sort, order, userId, goodsId }) }),
      transformResponse: (r: ApiEnvelope<PagedList<IFootprint>>) => withIsoAddTime(r?.data ?? emptyPage<IFootprint>()),
    }),
    listFeedback: builder.query<PagedList<IFeedback>, EngagementListParams>({
      query: ({ page, limit, sort, order, userId, username }) => ({ url: '/feedback/list', params: clean({ page, limit, sort, order, userId, username }) }),
      transformResponse: (r: ApiEnvelope<PagedList<IFeedback>>) => withIsoAddTime(r?.data ?? emptyPage<IFeedback>()),
    }),
  }),
});

export const { useListCollectsQuery, useListFootprintsQuery, useListFeedbackQuery } = adminEngagementApi;
