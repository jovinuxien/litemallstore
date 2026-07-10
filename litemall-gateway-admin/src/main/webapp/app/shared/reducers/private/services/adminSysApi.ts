import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { IAdmin, ILog, INotice, IRole, IRoleOption, IStorage, PagedList } from 'app/shared/model/admin/promotion-system.model';
import { getAdminToken } from 'app/shared/reducers/admin-auth';

// RTK Query client for the admin system verticals (admin accounts / notice /
// log / role / storage), served through the gateway under '/srv/private/admin/*'.
// admin/notice/log/role are hosted at the gateway edge
// (litemall-gatewayadmin/web/admin/*); storage is served by
// litemall-goods-management (AdminStorageController). Admin JWT attached as a
// Bearer token; the edge enforces ROLE_ADMIN on '/srv/private/admin/**'. The
// operation-log rows are produced by AdminAuditLogFilter on every admin
// mutation through this gateway.

export interface ListParams {
  page: number;
  limit: number;
  sort: string;
  order: 'asc' | 'desc';
}

export interface AdminListParams extends ListParams {
  username?: string;
}
export interface NoticeListParams extends ListParams {
  title?: string;
  content?: string;
}
export interface LogListParams extends ListParams {
  name?: string;
}
export interface RoleListParams extends ListParams {
  name?: string;
}
export interface StorageListParams extends ListParams {
  key?: string;
  name?: string;
}

export interface ApiEnvelope<T = unknown> {
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

export const adminSysApi = createApi({
  reducerPath: 'adminSysApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      return headers;
    },
  }),
  tagTypes: ['Admin', 'Notice', 'Role', 'Storage'],
  endpoints: builder => ({
    // ----- Admin accounts ----------------------------------------------
    listAdmins: builder.query<PagedList<IAdmin>, AdminListParams>({
      query: ({ page, limit, sort, order, username }) => ({ url: '/admin/list', params: clean({ page, limit, sort, order, username }) }),
      transformResponse: (r: ApiEnvelope<PagedList<IAdmin>>) => r?.data ?? emptyPage<IAdmin>(),
      providesTags: ['Admin'],
    }),
    readAdmin: builder.query<IAdmin, number | string>({
      query: id => ({ url: '/admin/read', params: { id } }),
      transformResponse: (r: ApiEnvelope<IAdmin>) => r?.data ?? {},
    }),
    createAdmin: builder.mutation<ApiEnvelope<IAdmin>, IAdmin>({
      query: body => ({ url: '/admin/create', method: 'POST', body }),
      invalidatesTags: ['Admin'],
    }),
    updateAdmin: builder.mutation<ApiEnvelope<IAdmin>, IAdmin>({
      query: body => ({ url: '/admin/update', method: 'POST', body }),
      invalidatesTags: ['Admin'],
    }),
    deleteAdmin: builder.mutation<ApiEnvelope, IAdmin>({
      query: body => ({ url: '/admin/delete', method: 'POST', body }),
      invalidatesTags: ['Admin'],
    }),

    // ----- Notice -------------------------------------------------------
    listNotices: builder.query<PagedList<INotice>, NoticeListParams>({
      query: ({ page, limit, sort, order, title, content }) => ({ url: '/notice/list', params: clean({ page, limit, sort, order, title, content }) }),
      transformResponse: (r: ApiEnvelope<PagedList<INotice>>) => r?.data ?? emptyPage<INotice>(),
      providesTags: ['Notice'],
    }),
    createNotice: builder.mutation<ApiEnvelope<INotice>, INotice>({
      query: body => ({ url: '/notice/create', method: 'POST', body }),
      invalidatesTags: ['Notice'],
    }),
    updateNotice: builder.mutation<ApiEnvelope<INotice>, INotice>({
      query: body => ({ url: '/notice/update', method: 'POST', body }),
      invalidatesTags: ['Notice'],
    }),
    deleteNotice: builder.mutation<ApiEnvelope, INotice>({
      query: body => ({ url: '/notice/delete', method: 'POST', body }),
      invalidatesTags: ['Notice'],
    }),

    // ----- Log (read-only) ----------------------------------------------
    listLogs: builder.query<PagedList<ILog>, LogListParams>({
      query: ({ page, limit, sort, order, name }) => ({ url: '/log/list', params: clean({ page, limit, sort, order, name }) }),
      transformResponse: (r: ApiEnvelope<PagedList<ILog>>) => r?.data ?? emptyPage<ILog>(),
    }),

    // ----- Role ---------------------------------------------------------
    listRoles: builder.query<PagedList<IRole>, RoleListParams>({
      query: ({ page, limit, sort, order, name }) => ({ url: '/role/list', params: clean({ page, limit, sort, order, name }) }),
      transformResponse: (r: ApiEnvelope<PagedList<IRole>>) => r?.data ?? emptyPage<IRole>(),
      providesTags: ['Role'],
    }),
    roleOptions: builder.query<IRoleOption[], void>({
      query: () => ({ url: '/role/options' }),
      transformResponse: (r: ApiEnvelope<PagedList<IRoleOption>>) => r?.data?.list ?? [],
    }),
    createRole: builder.mutation<ApiEnvelope<IRole>, IRole>({
      query: body => ({ url: '/role/create', method: 'POST', body }),
      invalidatesTags: ['Role'],
    }),
    updateRole: builder.mutation<ApiEnvelope<IRole>, IRole>({
      query: body => ({ url: '/role/update', method: 'POST', body }),
      invalidatesTags: ['Role'],
    }),
    deleteRole: builder.mutation<ApiEnvelope, IRole>({
      query: body => ({ url: '/role/delete', method: 'POST', body }),
      invalidatesTags: ['Role'],
    }),

    // ----- Storage (served by goods-management) -------------------------
    listStorage: builder.query<PagedList<IStorage>, StorageListParams>({
      query: ({ page, limit, sort, order, key, name }) => ({ url: '/storage/list', params: clean({ page, limit, sort, order, key, name }) }),
      transformResponse: (r: ApiEnvelope<PagedList<IStorage>>) => r?.data ?? emptyPage<IStorage>(),
      providesTags: ['Storage'],
    }),
    uploadStorage: builder.mutation<ApiEnvelope<IStorage>, FormData>({
      query: body => ({ url: '/storage/create', method: 'POST', body }),
      invalidatesTags: ['Storage'],
    }),
    deleteStorage: builder.mutation<ApiEnvelope, IStorage>({
      query: body => ({ url: '/storage/delete', method: 'POST', body }),
      invalidatesTags: ['Storage'],
    }),
  }),
});

export const {
  useListAdminsQuery,
  useReadAdminQuery,
  useCreateAdminMutation,
  useUpdateAdminMutation,
  useDeleteAdminMutation,
  useListNoticesQuery,
  useCreateNoticeMutation,
  useUpdateNoticeMutation,
  useDeleteNoticeMutation,
  useListLogsQuery,
  useListRolesQuery,
  useRoleOptionsQuery,
  useCreateRoleMutation,
  useUpdateRoleMutation,
  useDeleteRoleMutation,
  useListStorageQuery,
  useUploadStorageMutation,
  useDeleteStorageMutation,
} = adminSysApi;
