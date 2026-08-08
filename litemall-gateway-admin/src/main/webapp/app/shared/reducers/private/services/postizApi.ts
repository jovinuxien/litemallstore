import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { getAdminToken } from 'app/shared/reducers/admin-auth';
import { fromServerDateTime } from 'app/shared/util/server-datetime';

// Wave 17: RTK Query client for the Postiz social-publishing surface, served
// by promotion-service under '/srv/private/admin/promotion/postiz' (the
// gateway's '/srv/**' catch-all — no route change). Coded to the Wave-17
// CONTRACT, not to the promotion branch: litemall {errno,errmsg,data}
// envelope everywhere; Postiz env absent ⇒ typed "not configured" errno,
// which the UI must treat exactly like enabled:false (feature hidden, never
// broken).

export interface IPostizStatus {
  enabled: boolean;
  channelCount?: number;
}

export interface IPostizChannel {
  integrationId: string;
  identifier?: string;
  name?: string;
  picture?: string;
  supported?: boolean;
  reason?: string;
}

export interface PostizBatchCommand {
  goodsIds: number[];
  channelIds: string[];
  /** UTC ISO-8601 instant of the first post. */
  startTime: string;
  intervalMinutes: number;
}

/**
 * Wave 20 page-source alternative body for the SAME /preview and /publish
 * endpoints: one post promoting a DIY page (page name + /page/<id> link +
 * hero image). Groupon-category pages come back REFUSED with a typed errno —
 * the UI shows that errmsg verbatim.
 */
export interface PostizPageCommand {
  pageId: number;
  channelIds: string[];
  /** UTC ISO-8601 instant of the post. */
  startTime: string;
}

export type PostizCommand = PostizBatchCommand | PostizPageCommand;

export interface IPostizPreviewChannel {
  integrationId: string;
  content?: string;
  settings?: Record<string, unknown>;
}

export interface IPostizPreviewItem {
  /** Product-source batches carry goodsId; page-source batches carry pageId. */
  goodsId?: number;
  pageId?: number;
  name?: string;
  picUrl?: string;
  scheduleAt?: string;
  /** Non-blocking, e.g. the "posted N days ago" dedup warning. */
  warnings?: string[];
  perChannel?: IPostizPreviewChannel[];
}

export interface IPostizPreview {
  batch: IPostizPreviewItem[];
  warnings?: string[];
}

export interface IPostizPublishChannelResult {
  integrationId: string;
  ok?: boolean;
  postizPostId?: string;
  /** Postiz validation errors surface here VERBATIM — render as-is. */
  error?: string;
}

export interface IPostizPublishResult {
  /** Product-source results carry goodsId; page-source results carry pageId. */
  goodsId?: number;
  pageId?: number;
  scheduleAt?: string;
  channels?: IPostizPublishChannelResult[];
}

export interface IPostizPublish {
  results: IPostizPublishResult[];
}

// One row of litemall_postiz_post via GET /log — the contract fixes the
// column set but not the JSON casing details, so keep the row open.
export interface IPostizLogRow {
  id?: number;
  goodsId?: number;
  /** Wave 20 — set on page-source posts (V55 litemall_postiz_post.page_id). */
  pageId?: number;
  categoryId?: number;
  integrationId?: string;
  identifier?: string;
  postizPostId?: string;
  scheduleTime?: string;
  status?: string;
  error?: string;
  addTime?: string;
  [key: string]: unknown;
}

export interface PostizLogResponse {
  total: number;
  pages: number;
  limit: number;
  page: number;
  list: IPostizLogRow[];
}

interface ApiEnvelope<T> {
  errno: number;
  errmsg: string;
  data?: T;
}

// promotion-service manager DTOs serialize LocalDateTime as numeric arrays
// depending on the module's Jackson setup — normalise every datetime field.
type Raw = Record<string, unknown>;

const toPreviewItem = (r: Raw): IPostizPreviewItem =>
  ({ ...r, scheduleAt: fromServerDateTime(r.scheduleAt) }) as IPostizPreviewItem;

const toPublishResult = (r: Raw): IPostizPublishResult =>
  ({ ...r, scheduleAt: fromServerDateTime(r.scheduleAt) }) as IPostizPublishResult;

const toLogRow = (r: Raw): IPostizLogRow =>
  ({
    ...r,
    scheduleTime: fromServerDateTime(r.scheduleTime),
    addTime: fromServerDateTime(r.addTime),
  }) as IPostizLogRow;

export const postizApi = createApi({
  reducerPath: 'postizApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin/promotion/postiz',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) {
        headers.set('Authorization', `Bearer ${token}`);
      }
      return headers;
    },
  }),
  tagTypes: ['PostizLog'],
  endpoints: builder => ({
    // The UI visibility switch: ANY non-ok answer (typed errno, transport
    // error, missing body) reads as disabled — the panel hides, never breaks.
    getPostizStatus: builder.query<IPostizStatus, void>({
      query: () => ({ url: '/status' }),
      transformResponse: (r: ApiEnvelope<IPostizStatus>) => (r?.errno === 0 && r.data ? r.data : { enabled: false }),
    }),
    getPostizChannels: builder.query<IPostizChannel[], void>({
      query: () => ({ url: '/channels' }),
      transformResponse: (r: ApiEnvelope<{ list: IPostizChannel[] }>) => r?.data?.list ?? [],
    }),
    // ZERO side effects server-side; still a mutation so nothing caches a
    // stale preview. Callers check the envelope with errnoMessage.
    previewPostiz: builder.mutation<ApiEnvelope<IPostizPreview>, PostizCommand>({
      query: body => ({ url: '/preview', method: 'POST', body }),
      transformResponse: (r: ApiEnvelope<IPostizPreview>) =>
        r?.data ? { ...r, data: { ...r.data, batch: (r.data.batch ?? []).map(b => toPreviewItem(b as unknown as Raw)) } } : r,
    }),
    publishPostiz: builder.mutation<ApiEnvelope<IPostizPublish>, PostizCommand>({
      query: body => ({ url: '/publish', method: 'POST', body }),
      transformResponse: (r: ApiEnvelope<IPostizPublish>) =>
        r?.data ? { ...r, data: { results: (r.data.results ?? []).map(x => toPublishResult(x as unknown as Raw)) } } : r,
      invalidatesTags: ['PostizLog'],
    }),
    getPostizLog: builder.query<PostizLogResponse, { page: number; limit: number }>({
      query: ({ page, limit }) => ({ url: '/log', params: { page, limit } }),
      transformResponse: (r: ApiEnvelope<PostizLogResponse>) => {
        const d = r?.data ?? { total: 0, pages: 0, limit: 0, page: 1, list: [] };
        return { ...d, list: (d.list ?? []).map(row => toLogRow(row as unknown as Raw)) };
      },
      providesTags: ['PostizLog'],
    }),
  }),
});

export const {
  useGetPostizStatusQuery,
  useGetPostizChannelsQuery,
  usePreviewPostizMutation,
  usePublishPostizMutation,
  useGetPostizLogQuery,
} = postizApi;
