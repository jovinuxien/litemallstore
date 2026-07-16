import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { PagedList } from 'app/shared/model/admin/promotion-system.model';
import { getAdminToken } from 'app/shared/reducers/admin-auth';
import { fromServerDateTime } from 'app/shared/util/server-datetime';

// RTK Query client for the upstream-parity admin surfaces added in Wave 4:
//   - Topic CRUD          → litemall-goods-management  /srv/private/admin/topic/*
//   - Search history list → litemall-goods-management  /srv/private/admin/history/list
//   - Comment reply       → litemall-goods-management  /srv/private/admin/comment/reply
//   - System config       → gateway edge               /srv/private/admin/config/{mall,express,order}
//   - Profile password    → gateway edge               /srv/private/admin/profile/password
//   - Notice inbox        → gateway edge               /srv/private/admin/profile/{nnotice,lsnotice,catnotice,bcatnotice,rmnotice,brmnotice}
// Topic/history/reply contracts come from the committed handoff spec
// (litemall-goods-management/docs/handoff-content-endpoints.md §5–§7); the
// config/profile shapes were live-verified against this gateway. Legacy
// envelope {errno, errmsg, data} — HTTP 200 even on business errors, so
// mutations return the raw envelope for the views to inspect (e.g. errno 622
// "reply exists", errno 402 unknown config key, errno 605 wrong old password).

export interface ApiEnvelope<T = unknown> {
  errno: number;
  errmsg: string;
  data?: T;
}

// ----- Topic (litemall_topic) -------------------------------------------

export interface ITopic {
  id?: number;
  title?: string;
  subtitle?: string;
  price?: number;
  readCount?: string;
  picUrl?: string;
  sortOrder?: number;
  /** Related goods ids. The server stores an int array; be defensive on read. */
  goods?: number[] | string;
  content?: string;
  addTime?: string;
  updateTime?: string;
  deleted?: boolean;
}

export interface TopicListParams {
  page: number;
  limit: number;
  sort?: string;
  order?: 'asc' | 'desc';
  title?: string;
  subtitle?: string;
}

// ----- Search history (litemall_search_history) --------------------------

export interface IHistory {
  id?: number;
  userId?: number;
  keyword?: string;
  from?: string;
  addTime?: string;
}

export interface HistoryListParams {
  page: number;
  limit: number;
  userId?: string;
  keyword?: string;
}

// ----- System config ------------------------------------------------------

export type ConfigGroup = 'mall' | 'express' | 'order' | 'brokerage';
export type ConfigMap = Record<string, string>;

// ----- Notice inbox (litemall_notice_admin rows) --------------------------

export interface INoticeInboxRow {
  /** litemall_notice_admin row id — this is what rmnotice/brmnotice want. */
  id?: number;
  noticeId?: number;
  noticeTitle?: string;
  /** null/absent while unread. */
  readTime?: string;
  addTime?: string;
}

export interface NoticeInboxParams {
  type: 'all' | 'read' | 'unread';
  title?: string;
  page: number;
  limit: number;
}

export interface INoticeDetail {
  title?: string;
  content?: string;
  time?: string;
  admin?: string;
  avatar?: string;
}

const emptyPage = <T>(): PagedList<T> => ({ list: [], total: 0, page: 1, limit: 0, pages: 0 });

const clean = (params: Record<string, unknown>): Record<string, unknown> => {
  const out: Record<string, unknown> = {};
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') out[k] = v;
  });
  return out;
};

// Some rows serialize LocalDateTime as a numeric array — normalise to ISO.
const isoTimes = <T extends { addTime?: unknown; readTime?: unknown; updateTime?: unknown }>(page: PagedList<T>): PagedList<T> => ({
  ...page,
  list: (page.list ?? []).map(row => ({
    ...row,
    addTime: fromServerDateTime(row.addTime),
    readTime: fromServerDateTime(row.readTime),
    updateTime: fromServerDateTime(row.updateTime),
  })),
});

// GET /topic/read mirrors AdminBrandController (data = the row), but be
// defensive against an upstream-style {topic, goodsList} nesting.
const unwrapTopic = (data: unknown): ITopic => {
  const d = data as { topic?: ITopic } & ITopic;
  const row = d && typeof d === 'object' && d.topic && typeof d.topic === 'object' ? d.topic : d;
  return row ? { ...row, addTime: fromServerDateTime(row.addTime), updateTime: fromServerDateTime(row.updateTime) } : {};
};

const okOnly = (tags: ('Topic' | 'Config' | 'NoticeCount' | 'NoticeInbox' | { type: 'Config'; id: string })[]) =>
  (result?: ApiEnvelope) => (result && result.errno === 0 ? tags : []);

export const adminParityApi = createApi({
  reducerPath: 'adminParityApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      return headers;
    },
  }),
  tagTypes: ['Topic', 'Config', 'NoticeCount', 'NoticeInbox'],
  endpoints: builder => ({
    // ----- Topic CRUD ----------------------------------------------------
    listTopics: builder.query<PagedList<ITopic>, TopicListParams>({
      query: ({ page, limit, sort, order, title, subtitle }) => ({
        url: '/topic/list',
        params: clean({ page, limit, sort, order, title, subtitle }),
      }),
      transformResponse: (r: ApiEnvelope<PagedList<ITopic>>) => isoTimes(r?.data ?? emptyPage<ITopic>()),
      providesTags: ['Topic'],
    }),
    readTopic: builder.query<ITopic, number | string>({
      query: id => ({ url: '/topic/read', params: { id } }),
      transformResponse: (r: ApiEnvelope<unknown>) => unwrapTopic(r?.data),
    }),
    createTopic: builder.mutation<ApiEnvelope<ITopic>, ITopic>({
      query: body => ({ url: '/topic/create', method: 'POST', body }),
      invalidatesTags: okOnly(['Topic']),
    }),
    updateTopic: builder.mutation<ApiEnvelope<ITopic>, ITopic>({
      query: body => ({ url: '/topic/update', method: 'POST', body }),
      invalidatesTags: okOnly(['Topic']),
    }),
    deleteTopic: builder.mutation<ApiEnvelope, { id: number }>({
      query: body => ({ url: '/topic/delete', method: 'POST', body }),
      invalidatesTags: okOnly(['Topic']),
    }),
    batchDeleteTopics: builder.mutation<ApiEnvelope, { ids: number[] }>({
      query: body => ({ url: '/topic/batch-delete', method: 'POST', body }),
      invalidatesTags: okOnly(['Topic']),
    }),

    // ----- Search history (read-only) -------------------------------------
    listHistory: builder.query<PagedList<IHistory>, HistoryListParams>({
      query: ({ page, limit, userId, keyword }) => ({ url: '/history/list', params: clean({ page, limit, userId, keyword }) }),
      transformResponse: (r: ApiEnvelope<PagedList<IHistory>>) => isoTimes(r?.data ?? emptyPage<IHistory>()),
    }),

    // ----- Comment reply ---------------------------------------------------
    // Sets adminContent ONCE; a second reply comes back errno 622 ("reply
    // exists") — the view surfaces errmsg. Raw envelope on purpose.
    replyComment: builder.mutation<ApiEnvelope, { commentId: number; content: string }>({
      query: body => ({ url: '/comment/reply', method: 'POST', body }),
    }),

    // ----- System config ---------------------------------------------------
    getConfigGroup: builder.query<ConfigMap, ConfigGroup>({
      query: group => ({ url: `/config/${group}` }),
      transformResponse: (r: ApiEnvelope<ConfigMap>) => r?.data ?? {},
      providesTags: (result, error, group) => [{ type: 'Config', id: group }],
    }),
    // errno 402 with errmsg naming the key when a key doesn't belong to the
    // group; only refetch (invalidate) on a clean save.
    updateConfigGroup: builder.mutation<ApiEnvelope, { group: ConfigGroup; values: ConfigMap }>({
      query: ({ group, values }) => ({ url: `/config/${group}`, method: 'POST', body: values }),
      invalidatesTags: (result, error, { group }) => (result && result.errno === 0 ? [{ type: 'Config', id: group }] : []),
    }),

    // ----- Profile: password ----------------------------------------------
    // errno 605 wrong old password, 602 new password too short (<6).
    changePassword: builder.mutation<ApiEnvelope, { oldPassword: string; newPassword: string }>({
      query: body => ({ url: '/profile/password', method: 'POST', body }),
    }),

    // ----- Profile: notice inbox -------------------------------------------
    unreadNoticeCount: builder.query<number, void>({
      query: () => ({ url: '/profile/nnotice' }),
      transformResponse: (r: ApiEnvelope<number>) => r?.data ?? 0,
      providesTags: ['NoticeCount'],
    }),
    listNoticeInbox: builder.query<PagedList<INoticeInboxRow>, NoticeInboxParams>({
      query: ({ type, title, page, limit }) => ({ url: '/profile/lsnotice', params: clean({ type, title, page, limit }) }),
      transformResponse: (r: ApiEnvelope<PagedList<INoticeInboxRow>>) => isoTimes(r?.data ?? emptyPage<INoticeInboxRow>()),
      providesTags: ['NoticeInbox'],
    }),
    // Returns the notice body AND marks the inbox row read.
    readNotice: builder.mutation<ApiEnvelope<INoticeDetail>, { noticeId: number }>({
      query: body => ({ url: '/profile/catnotice', method: 'POST', body }),
      invalidatesTags: okOnly(['NoticeInbox', 'NoticeCount']),
    }),
    markNoticesRead: builder.mutation<ApiEnvelope, { ids: number[] }>({
      query: body => ({ url: '/profile/bcatnotice', method: 'POST', body }),
      invalidatesTags: okOnly(['NoticeInbox', 'NoticeCount']),
    }),
    // id / ids = the litemall_notice_admin inbox-row id(s), not noticeId.
    removeNotice: builder.mutation<ApiEnvelope, { id: number }>({
      query: body => ({ url: '/profile/rmnotice', method: 'POST', body }),
      invalidatesTags: okOnly(['NoticeInbox', 'NoticeCount']),
    }),
    removeNotices: builder.mutation<ApiEnvelope, { ids: number[] }>({
      query: body => ({ url: '/profile/brmnotice', method: 'POST', body }),
      invalidatesTags: okOnly(['NoticeInbox', 'NoticeCount']),
    }),
  }),
});

export const {
  useListTopicsQuery,
  useReadTopicQuery,
  useCreateTopicMutation,
  useUpdateTopicMutation,
  useDeleteTopicMutation,
  useBatchDeleteTopicsMutation,
  useListHistoryQuery,
  useReplyCommentMutation,
  useGetConfigGroupQuery,
  useUpdateConfigGroupMutation,
  useChangePasswordMutation,
  useUnreadNoticeCountQuery,
  useListNoticeInboxQuery,
  useReadNoticeMutation,
  useMarkNoticesReadMutation,
  useRemoveNoticeMutation,
  useRemoveNoticesMutation,
} = adminParityApi;
